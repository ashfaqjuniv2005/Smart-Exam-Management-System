package smartassessment.service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import smartassessment.model.AssessmentResult;
import smartassessment.model.Exam;
import smartassessment.model.Question;
import smartassessment.model.Student;
import smartassessment.model.StudentSession;
import smartassessment.model.Submission;

/** Business logic: exam checks, login rules, marking, results and the automatic question generator. */
public class ExamService {
    private final ExamRepository repository;

    public ExamService(ExamRepository repository) { this.repository = repository; }

    public ExamRepository getRepository() { return repository; }

    // ================================================================ exams
    /** Returns a problem description, or null if the exam can be used. */
    public String validateExam(Exam e) {
        if (e.getTitle().trim().isEmpty()) return "Please enter the exam title.";
        if (e.getCode().trim().isEmpty()) return "Please enter an exam code.";
        if (e.getQuestions().isEmpty()) {
            return "The exam has no questions. Click \"Manage Questions\" to add MCQ / written questions,"
                    + " or generate them automatically.";
        }
        return null;
    }

    public void saveExam(Exam e) throws IOException { repository.saveExam(e); }

    public Exam loadExam(String code) throws IOException, ClassNotFoundException {
        return repository.loadExam(code);
    }

    public List<String> listExamCodes() { return repository.listExamCodes(); }

    // ================================================================ login
    /** Returns the reason why the student may not enter, or null if the login is accepted. */
    public String validateLogin(Exam exam, Student s, StudentSession existing, boolean started, boolean ended) {
        if (s == null || s.getName().trim().isEmpty() || s.getRoll().trim().isEmpty()
                || s.getBatch().trim().isEmpty()) {
            return "Name, batch and exam roll are required.";
        }
        if (!exam.getCode().equalsIgnoreCase(s.getExamCode().trim())) return "Invalid exam code.";
        if (!exam.isRollAllowed(s.getRoll())) return "You do not have access to this exam.";
        if (existing != null) {
            if (existing.isSubmitted()) return "You have already submitted this exam.";
            if (existing.isConnected()) return "This roll is already logged in on another computer.";
        } else {
            // also covers submissions saved in an earlier sitting (server restarted, exam restarted ...)
            if (repository.hasSubmission(exam.getCode(), s.getRoll())) {
                return "You have already submitted this exam.";
            }
            if (!started) return "The exam has not started yet. Please wait for the teacher.";
            if (ended) return "The exam has ended. Ask your teacher to restart it for you.";
        }
        return null;
    }

    // ========================================================== submissions
    /** Builds, stores and marks the submission of a student. */
    public Submission recordSubmission(Exam exam, StudentSession session, boolean auto, boolean byServer)
            throws IOException {
        Submission sub = session.buildSubmission(exam.getCode(), auto, byServer);
        if (exam.hasMcq()) {
            int n = exam.getMcqQuestions().size();
            int[] answers = new int[n];
            Arrays.fill(answers, -1);
            int[] got = sub.getMcqAnswers();
            if (got != null) {
                for (int i = 0; i < n && i < got.length; i++) answers[i] = got[i];
            }
            sub.setMcqAnswers(answers);
        }
        List<String> written = new ArrayList<>(sub.getWrittenAnswers());
        while (written.size() < exam.getWrittenQuestions().size()) written.add("");
        sub.setWrittenAnswers(written);
        repository.saveSubmission(sub);
        repository.saveResult(evaluate(exam, sub));
        session.setSubmitted(true);
        return sub;
    }

    public List<Submission> loadSubmissions(String examCode) { return repository.loadSubmissions(examCode); }

    /** Marks the MCQ part and keeps the written-part marks the teacher may already have given. */
    public AssessmentResult evaluate(Exam exam, Submission sub) {
        AssessmentResult previous = repository.loadResult(exam.getCode(), sub.getStudent().getRoll());
        AssessmentResult r = new AssessmentResult(sub.getStudent(), exam.getCode());
        r.setMcqTotal(exam.getMcqTotalMarks());
        r.setMcqScore(exam.hasMcq() ? exam.scoreMcq(sub.getMcqAnswers()) : 0);
        r.setWrittenTotal(exam.getWrittenTotalMarks());
        r.setCodingTotal(exam.getCodingTotalMarks());
        r.setWarnings(sub.getWarnings());
        if (previous != null) {
            r.setWrittenMarks(previous.getWrittenMarks());
            r.setCodingMarks(previous.getCodingMarks());
            r.setRemarks(previous.getRemarks());
        }
        return r;
    }

    /** Teacher gives marks for the written part. */
    public AssessmentResult setWrittenMarks(Exam exam, Submission sub, int marks) throws IOException {
        AssessmentResult r = evaluate(exam, sub);
        r.setWrittenMarks(Math.max(0, Math.min(marks, exam.getWrittenTotalMarks())));
        repository.saveResult(r);
        return r;
    }

    /** Teacher gives marks for the coding part. */
    public AssessmentResult setCodingMarks(Exam exam, Submission sub, int marks) throws IOException {
        AssessmentResult r = evaluate(exam, sub);
        r.setCodingMarks(Math.max(0, Math.min(marks, exam.getCodingTotalMarks())));
        repository.saveResult(r);
        return r;
    }

    /** Text report of the MCQ answers of one student. */
    public String buildMcqSheet(Exam exam, Submission sub) {
        AssessmentResult r = evaluate(exam, sub);
        StringBuilder sb = new StringBuilder();
        sb.append("MCQ score: ").append(r.getMcqScore()).append(" / ").append(r.getMcqTotal()).append("\n\n");
        List<Question> qs = exam.getMcqQuestions();
        int[] ans = sub.getMcqAnswers() == null ? new int[0] : sub.getMcqAnswers();
        for (int i = 0; i < qs.size(); i++) {
            Question q = qs.get(i);
            int chosen = i < ans.length ? ans[i] : -1;
            String tag = chosen == q.getCorrectIndex() ? "[correct]   " : (chosen < 0 ? "[no answer] " : "[wrong]     ");
            sb.append(tag).append("Q").append(i + 1).append(". ").append(q.getText()).append("\n");
            sb.append("     Student answered: ")
              .append(chosen < 0 ? "-" : (char) ('A' + chosen) + ". " + q.getOptions().get(chosen)).append("\n");
            sb.append("     Correct answer:   ").append((char) ('A' + q.getCorrectIndex())).append(". ")
              .append(q.getOptions().get(q.getCorrectIndex())).append("\n\n");
        }
        return sb.toString();
    }

    /** Text report of the written answers of one student. */
    public String buildWrittenSheet(Exam exam, Submission sub) {
        StringBuilder sb = new StringBuilder();
        List<Question> qs = exam.getWrittenQuestions();
        for (int i = 0; i < qs.size(); i++) {
            String ans = i < sub.getWrittenAnswers().size() ? sub.getWrittenAnswers().get(i) : "";
            sb.append("Q").append(i + 1).append(" (").append(qs.get(i).getMarks()).append(" marks)\n")
              .append(qs.get(i).getText()).append("\n\nAnswer:\n")
              .append(ans == null || ans.trim().isEmpty() ? "(no answer)" : ans)
              .append("\n\n------------------------------------------------------------\n\n");
        }
        return sb.toString();
    }

    public void exportCsv(Exam exam, List<Submission> subs, File target) throws IOException {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Roll", "Name", "Batch", "Submitted At", "Warnings", "Type",
                "MCQ Score", "MCQ Total", "Written Marks", "Written Total", "Coding Marks", "Coding Total",
                "Total Score", "Total Marks"});
        for (Submission s : subs) {
            AssessmentResult r = evaluate(exam, s);
            rows.add(new String[]{
                    s.getStudent().getRoll(), s.getStudent().getName(), s.getStudent().getBatch(),
                    fmt.format(new Date(s.getSubmittedAt())), String.valueOf(s.getWarnings()),
                    s.isCollectedByServer() ? "Collected by server" : (s.isAutoSubmitted() ? "Auto" : "Student"),
                    String.valueOf(r.getMcqScore()), String.valueOf(r.getMcqTotal()),
                    r.isWrittenGraded() ? String.valueOf(r.getWrittenMarks()) : "not graded",
                    String.valueOf(r.getWrittenTotal()),
                    r.isCodingGraded() ? String.valueOf(r.getCodingMarks()) : "not graded",
                    String.valueOf(r.getCodingTotal()), String.valueOf(r.getTotalScore()),
                    String.valueOf(r.getTotalMarks())});
        }
        repository.writeCsv(target, rows);
    }

    // ================================================== question generator
    /** One kind of generated question (random numbers inside a fixed pattern). */
    private interface Template {
        Question make(Random r, int marks);
    }

    private static final List<Template> MCQ_TEMPLATES = new ArrayList<>();
    private static final List<Template> CODING_TEMPLATES = new ArrayList<>();
    private static final List<Template> WRITTEN_TEMPLATES = new ArrayList<>();

    static {
        // Written (theory) questions: a topic inside a question pattern, or a comparison of two topics.
        final String[] topics = {"encapsulation", "inheritance", "polymorphism", "abstraction", "a constructor",
            "an interface", "an abstract class", "method overloading", "method overriding", "exception handling",
            "the static keyword", "the final keyword", "the this keyword", "the super keyword", "a package",
            "access modifiers", "an ArrayList", "a HashMap", "recursion", "an array", "the String class", "the JVM",
            "garbage collection", "multithreading", "file handling", "generics", "an enum", "an inner class",
            "a wrapper class", "type casting", "the try-catch-finally block", "a custom exception",
            "the Object class", "a singleton class", "the Comparable interface", "an iterator", "the for-each loop",
            "a two-dimensional array", "a linked list", "a stack", "a queue", "a copy constructor",
            "method chaining", "an anonymous class", "the instanceof operator", "operator precedence",
            "a lambda expression", "a static method", "an instance variable", "a local variable"};
        final String[] frames = {"Define %s and explain it with a suitable example.",
            "What is %s? Write two advantages of using it.",
            "Explain the role of %s in object-oriented programming.",
            "Write short notes on %s.",
            "Why do we use %s? Explain with a real-life example.",
            "Describe %s with a small Java code snippet.",
            "Explain %s in your own words and mention one common mistake students make with it.",
            "What is %s? Explain how and when it is used in a program.",
            "Discuss the importance of %s in writing clean programs.",
            "Explain %s step by step for a beginner.",
            "What problems can occur if %s is used wrongly? Explain.",
            "Give a short definition of %s and write three important points about it."};
        final String[][] pairs = {{"an abstract class", "an interface"}, {"overloading", "overriding"},
            {"ArrayList", "LinkedList"}, {"a class", "an object"}, {"the == operator", "the equals() method"},
            {"checked exceptions", "unchecked exceptions"}, {"final", "finally"}, {"a stack", "a queue"},
            {"an array", "an ArrayList"}, {"String", "StringBuilder"}, {"static methods", "instance methods"},
            {"a constructor", "a method"}, {"JDK", "JRE"}, {"inheritance", "composition"}, {"HashMap", "TreeMap"},
            {"break", "continue"}, {"while", "do-while"}, {"public", "private"}, {"super", "this"},
            {"throw", "throws"}, {"a process", "a thread"}, {"compile-time errors", "runtime errors"},
            {"pass by value", "pass by reference"}, {"local variables", "instance variables"},
            {"an array", "a linked list"}};
        final String[] compare = {"Differentiate between %s and %s with examples.",
            "What are the differences between %s and %s? Write at least three points.",
            "Compare %s and %s. When would you use each one?"};
        WRITTEN_TEMPLATES.add((r, m) -> Question.written(String.format(pick(r, frames), pick(r, topics)), m, null));
        WRITTEN_TEMPLATES.add((r, m) -> Question.written(String.format(pick(r, frames), pick(r, topics)), m, null));
        WRITTEN_TEMPLATES.add((r, m) -> {
            String[] p = pick(r, pairs);
            return Question.written(String.format(pick(r, compare), p[0], p[1]), m, null);
        });
        WRITTEN_TEMPLATES.add((r, m) -> Question.written("List and explain any " + rnd(r, 3, 8)
                + " features of the Java programming language.", m, null));
    }

    private static final String[] WORDS = {"programming", "java", "encapsulation", "polymorphism", "inheritance",
        "constructor", "abstraction", "interface", "compiler", "algorithm", "variable", "object", "function",
        "keyboard", "database", "network", "inheritance", "recursion", "iteration", "exception"};

    /** Concept questions: question, correct answer, three wrong answers. */
    private static final String[][] CONCEPTS = {
        {"Which keyword is used to inherit a class in Java?", "extends", "implements", "inherits", "super"},
        {"Which OOP concept means hiding internal details and showing only the essential features?", "Abstraction", "Polymorphism", "Inheritance", "Compilation"},
        {"Wrapping data and the methods that use it together in one unit (a class) is called:", "Encapsulation", "Abstraction", "Overloading", "Recursion"},
        {"A class that cannot be instantiated and may contain abstract methods is called:", "An abstract class", "A final class", "A wrapper class", "A static class"},
        {"Which keyword prevents a method from being overridden?", "final", "static", "abstract", "const"},
        {"What is method overloading?", "Same method name with different parameter lists in one class", "Same name and signature in a subclass", "A method that calls itself", "A method with no body"},
        {"What is method overriding?", "A subclass gives its own implementation of a superclass method", "Two methods with the same name but different parameters", "Calling a method from a constructor", "Defining a method inside another method"},
        {"Which keyword refers to the current object?", "this", "super", "self", "current"},
        {"Which keyword calls the superclass constructor?", "super", "this", "base", "parent"},
        {"A constructor in Java has:", "The same name as the class and no return type", "A void return type", "Any name", "To be declared static"},
        {"Which of the following is NOT a primitive data type in Java?", "String", "int", "boolean", "char"},
        {"Which keyword is used to create an object?", "new", "create", "malloc", "object"},
        {"Which access modifier makes a member visible only inside its own class?", "private", "public", "protected", "static"},
        {"Which method is the entry point of a Java program?", "main", "start", "run", "init"},
        {"Which exception is thrown when an integer is divided by zero?", "ArithmeticException", "NullPointerException", "NumberFormatException", "IOException"},
        {"Which exception occurs when an array index is outside the array range?", "ArrayIndexOutOfBoundsException", "NullPointerException", "ClassCastException", "ArithmeticException"},
        {"Which block always executes whether or not an exception occurs?", "finally", "catch", "try", "throw"},
        {"Which keyword is used to manually throw an exception?", "throw", "throws", "catch", "raise"},
        {"Which keyword declares that a method may throw an exception?", "throws", "throw", "try", "exception"},
        {"What is the default value of an int instance variable?", "0", "null", "1", "undefined"},
        {"What is the default value of a boolean instance variable?", "false", "true", "null", "0"},
        {"Which class implements a resizable array in Java?", "ArrayList", "HashMap", "HashSet", "TreeMap"},
        {"Which interface does a class implement so its objects can be sorted with compareTo()?", "Comparable", "Comparator", "Serializable", "Iterable"},
        {"Which keyword makes a variable shared by all objects of a class?", "static", "final", "volatile", "shared"},
        {"Which keyword makes a variable a constant in Java?", "final", "const", "static", "fixed"},
        {"Which class is commonly used to read keyboard input in Java?", "Scanner", "PrintWriter", "Display", "Printer"},
        {"What does JVM stand for?", "Java Virtual Machine", "Java Variable Method", "Joint Virtual Memory", "Java Visual Module"},
        {"Which statement about interfaces in Java is true?", "A class can implement multiple interfaces", "A class can extend multiple classes", "Interfaces can have constructors", "Interfaces are created with new"},
        {"Which kind of inheritance is NOT supported between classes in Java?", "Multiple inheritance", "Single inheritance", "Multilevel inheritance", "Hierarchical inheritance"},
        {"Runtime polymorphism is achieved through:", "Method overriding", "Method overloading", "Constructors", "Static methods"},
        {"Which is a valid way to declare an array in Java?", "int[] a = new int[5];", "int a = new int[5];", "array int a[5];", "int a(5);"},
        {"Which loop always executes its body at least once?", "do-while", "while", "for", "for-each"},
        {"What is the index of the first element of an array in Java?", "0", "1", "-1", "It depends on the type"},
        {"Which statement is used to include a package in a Java file?", "import", "include", "using", "require"},
        {"What is the result of 5 / 2 in Java when both are int?", "2", "2.5", "3", "2.0"},
        {"Which concept allows an object to take many forms?", "Polymorphism", "Encapsulation", "Compilation", "Serialization"},
        {"Which of these is a checked exception?", "IOException", "NullPointerException", "ArithmeticException", "ArrayIndexOutOfBoundsException"},
        {"Which class is the parent of all classes in Java?", "Object", "Class", "System", "Main"},
        {"In C++, which operator accesses a member through a pointer?", "->", ".", "::", "&"},
        {"In C++, which header is needed to use cout?", "<iostream>", "<stdio.h>", "<string>", "<conio.h>"},
        {"In C++, what does a destructor do?", "Cleans up when an object is destroyed", "Creates an object", "Copies an object", "Declares a class"},
        {"Which access specifier is the default for members of a C++ class?", "private", "public", "protected", "friend"},
        {"In C++, a function declared with the keyword virtual supports:", "Runtime polymorphism", "Only compile-time overloading", "Memory allocation", "Macro expansion"},
    };

    private static int rnd(Random r, int lo, int hi) { return lo + r.nextInt(hi - lo + 1); }

    private static <T> T pick(Random r, T[] items) { return items[r.nextInt(items.length)]; }

    /** MCQ with the given correct answer and a pool of wrong answers (3 are chosen). */
    private static Question choice(Random r, String text, String correct, List<String> wrongPool, int marks) {
        List<String> pool = new ArrayList<>();
        for (String w : wrongPool) if (!w.equals(correct) && !pool.contains(w)) pool.add(w);
        Collections.shuffle(pool, r);
        List<String> options = new ArrayList<>(pool.subList(0, Math.min(3, pool.size())));
        options.add(correct);
        Collections.shuffle(options, r);
        return Question.mcq(text, options, options.indexOf(correct), marks);
    }

    /** MCQ whose answer is a number; the wrong answers are close to the right one. */
    private static Question numeric(Random r, String text, long correct, int marks) {
        List<Long> deltas = new ArrayList<>(Arrays.asList(1L, -1L, 2L, -2L, 3L, 5L, -5L, 7L, 10L, -10L));
        Collections.shuffle(deltas, r);
        List<String> wrong = new ArrayList<>();
        for (long d : deltas) {
            long v = correct + d;
            if (v >= 0 || correct < 0) wrong.add(String.valueOf(v));
        }
        return choice(r, text, String.valueOf(correct), wrong, marks);
    }

    private static String list(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) sb.append(i > 0 ? ", " : "").append(a[i]);
        return sb.toString();
    }

    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int i = 2; (long) i * i <= n; i++) if (n % i == 0) return false;
        return true;
    }

    static {
        // ---- 1. operator precedence
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 2, 20), b = rnd(r, 2, 15), c = rnd(r, 2, 9);
            boolean cpp = r.nextBoolean();
            String code = cpp
                    ? "int a = " + a + ", b = " + b + ", c = " + c + ";\ncout << a + b * c;"
                    : "int a = " + a + ", b = " + b + ", c = " + c + ";\nSystem.out.println(a + b * c);";
            return numeric(r, "What is the output of the following " + (cpp ? "C++" : "Java") + " code?\n\n" + code,
                    a + b * c, m);
        });
        // ---- 2. integer division and remainder
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 10, 99), b = rnd(r, 2, 9);
            String correct = (a / b) + " " + (a % b);
            List<String> wrong = Arrays.asList((a % b) + " " + (a / b), (a / b + 1) + " " + (a % b),
                    (a / b) + " " + (a % b + 1), (a / b - 1) + " " + (a % b), (a / b) + "." + (a % b) + " 0");
            return choice(r, "What is the output?\n\nint a = " + a + ", b = " + b
                    + ";\nSystem.out.println(a / b + \" \" + a % b);", correct, wrong, m);
        });
        // ---- 3. loop sum (two variants)
        MCQ_TEMPLATES.add((r, m) -> {
            if (r.nextBoolean()) {
                int n = rnd(r, 3, 30);
                return numeric(r, "What is printed?\n\nint s = 0;\nfor (int i = 1; i <= " + n
                        + "; i++) {\n    s += i;\n}\nSystem.out.println(s);", n * (n + 1) / 2, m);
            }
            int n = rnd(r, 10, 40), k = rnd(r, 2, 5), s = 0;
            for (int i = 0; i < n; i += k) s += i;
            return numeric(r, "What is printed?\n\nint s = 0;\nfor (int i = 0; i < " + n + "; i += " + k
                    + ") {\n    s += i;\n}\nSystem.out.println(s);", s, m);
        });
        // ---- 4. how many times does a loop run
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 0, 10), b = a + rnd(r, 5, 40), c = rnd(r, 1, 6), count = 0;
            for (int i = a; i < b; i += c) count++;
            return numeric(r, "How many times does the loop body execute?\n\nfor (int i = " + a + "; i < " + b
                    + "; i += " + c + ") {\n    // body\n}", count, m);
        });
        // ---- 5. array element access
        MCQ_TEMPLATES.add((r, m) -> {
            int[] a = new int[5];
            for (int i = 0; i < a.length; i++) a[i] = rnd(r, 1, 50);
            int i = r.nextInt(5), j = r.nextInt(5);
            return numeric(r, "What is the output?\n\nint[] arr = {" + list(a) + "};\nSystem.out.println(arr["
                    + i + "] + arr[" + j + "]);", a[i] + a[j], m);
        });
        // ---- 6. String methods
        MCQ_TEMPLATES.add((r, m) -> {
            String w = pick(r, WORDS);
            int kind = r.nextInt(3);
            if (kind == 0) {
                return numeric(r, "What is the output?\n\nString s = \"" + w + "\";\nSystem.out.println(s.length());",
                        w.length(), m);
            }
            if (kind == 1) {
                int i = r.nextInt(w.length());
                List<String> wrong = new ArrayList<>();
                for (int d : new int[]{-1, 1, 2, -2}) {
                    int k = i + d;
                    if (k >= 0 && k < w.length()) wrong.add(String.valueOf(w.charAt(k)));
                }
                wrong.addAll(Arrays.asList("a", "e", "z", "q", "x"));
                return choice(r, "What is the output?\n\nString s = \"" + w + "\";\nSystem.out.println(s.charAt("
                        + i + "));", String.valueOf(w.charAt(i)), wrong, m);
            }
            int a = r.nextInt(w.length() - 3), b = Math.min(w.length(), a + rnd(r, 2, 3));
            List<String> wrong = Arrays.asList(w.substring(a + 1, b), w.substring(a, b - 1),
                    w.substring(a, Math.min(w.length(), b + 1)), "Runtime error", "Compile error", "null");
            return choice(r, "What is the output?\n\nString s = \"" + w + "\";\nSystem.out.println(s.substring("
                    + a + ", " + b + "));", w.substring(a, b), wrong, m);
        });
        // ---- 7. recursion: factorial
        MCQ_TEMPLATES.add((r, m) -> {
            int n = rnd(r, 3, 8);
            long f = 1;
            for (int i = 2; i <= n; i++) f *= i;
            return numeric(r, "What does factorial(" + n + ") return?\n\nstatic int factorial(int n) {\n"
                    + "    return (n <= 1) ? 1 : n * factorial(n - 1);\n}", f, m);
        });
        // ---- 8. Math.pow
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 2, 6), b = rnd(r, 2, 5);
            return numeric(r, "What is the output?\n\nSystem.out.println((int) Math.pow(" + a + ", " + b + "));",
                    (long) Math.pow(a, b), m);
        });
        // ---- 9. ternary operator
        MCQ_TEMPLATES.add((r, m) -> {
            int x = rnd(r, 1, 500);
            String correct = x % 2 == 0 ? "Even" : "Odd";
            return choice(r, "What is printed?\n\nint x = " + x + ";\nString r = (x % 2 == 0) ? \"Even\" : \"Odd\";\n"
                    + "System.out.println(r);", correct,
                    Arrays.asList(x % 2 == 0 ? "Odd" : "Even", "Compile error", "Runtime error"), m);
        });
        // ---- 10. pre / post increment
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 1, 30);
            String correct = (a + 2) + " " + (2 * a + 2);
            List<String> wrong = Arrays.asList((a + 1) + " " + (2 * a + 1), (a + 2) + " " + (2 * a),
                    (a + 1) + " " + (2 * a + 2), (a + 2) + " " + (2 * a + 3), (a + 1) + " " + (2 * a));
            return choice(r, "What is the output?\n\nint x = " + a + ";\nint y = x++ + ++x;\n"
                    + "System.out.println(x + \" \" + y);", correct, wrong, m);
        });
        // ---- 11. compound assignment
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 1, 20), b = rnd(r, 1, 10), c = rnd(r, 2, 5), d = rnd(r, 1, 15);
            return numeric(r, "What is the output?\n\nint x = " + a + ";\nx += " + b + ";\nx *= " + c + ";\nx -= " + d
                    + ";\nSystem.out.println(x);", (a + b) * c - d, m);
        });
        // ---- 12. maximum of an array
        MCQ_TEMPLATES.add((r, m) -> {
            Set<Integer> set = new LinkedHashSet<>();
            while (set.size() < 5) set.add(rnd(r, 1, 99));
            int[] a = new int[5];
            int k = 0, max = 0;
            for (int v : set) { a[k++] = v; max = Math.max(max, v); }
            List<String> wrong = new ArrayList<>();
            for (int v : a) wrong.add(String.valueOf(v));
            return choice(r, "What does this code print?\n\nint[] a = {" + list(a) + "};\nint max = a[0];\n"
                    + "for (int i = 1; i < a.length; i++) {\n    if (a[i] > max) max = a[i];\n}\n"
                    + "System.out.println(max);", String.valueOf(max), wrong, m);
        });
        // ---- 13. count even numbers
        MCQ_TEMPLATES.add((r, m) -> {
            int[] a = new int[6];
            int count = 0;
            for (int i = 0; i < a.length; i++) {
                a[i] = rnd(r, 1, 50);
                if (a[i] % 2 == 0) count++;
            }
            return numeric(r, "What does this code print?\n\nint[] a = {" + list(a) + "};\nint c = 0;\n"
                    + "for (int v : a) {\n    if (v % 2 == 0) c++;\n}\nSystem.out.println(c);", count, m);
        });
        // ---- 14. left shift
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 1, 9), b = rnd(r, 1, 5);
            return numeric(r, "What is the output?\n\nSystem.out.println(" + a + " << " + b + ");", a << b, m);
        });
        // ---- 15. decimal to binary
        MCQ_TEMPLATES.add((r, m) -> {
            int n = rnd(r, 5, 255);
            return choice(r, "What is the binary representation of the decimal number " + n + "?",
                    Integer.toBinaryString(n),
                    Arrays.asList(Integer.toBinaryString(n + 1), Integer.toBinaryString(n - 1),
                            Integer.toBinaryString(n + 2), Integer.toBinaryString(n - 2)), m);
        });
        // ---- 16. string concatenation vs addition
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 1, 9), b = rnd(r, 1, 9), c = rnd(r, 1, 9), d = rnd(r, 1, 9);
            String correct = (a + b) + "x" + c + d;
            return choice(r, "What is the output?\n\nSystem.out.println(" + a + " + " + b + " + \"x\" + " + c
                    + " + " + d + ");", correct,
                    Arrays.asList("" + a + b + "x" + c + d, (a + b) + "x" + (c + d), "Compile error"), m);
        });
        // ---- 17. method call
        MCQ_TEMPLATES.add((r, m) -> {
            int k = rnd(r, 1, 20), v = rnd(r, 2, 9);
            return numeric(r, "What is the output?\n\nstatic int f(int n) {\n    return n * n + " + k
                    + ";\n}\n// in main:\nSystem.out.println(f(" + v + "));", v * v + k, m);
        });
        // ---- 18. nested loops
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 2, 9);
            if (r.nextBoolean()) {
                int b = rnd(r, 2, 9);
                return numeric(r, "What is printed?\n\nint count = 0;\nfor (int i = 0; i < " + a
                        + "; i++)\n    for (int j = 0; j < " + b + "; j++)\n        count++;\nSystem.out.println(count);",
                        a * b, m);
            }
            return numeric(r, "What is printed?\n\nint count = 0;\nfor (int i = 1; i <= " + a
                    + "; i++)\n    for (int j = 1; j <= i; j++)\n        count++;\nSystem.out.println(count);",
                    a * (a + 1) / 2, m);
        });
        // ---- 19. halving loop
        MCQ_TEMPLATES.add((r, m) -> {
            int n = rnd(r, 8, 1000), x = n, c = 0;
            while (x > 1) { x /= 2; c++; }
            return numeric(r, "What is printed?\n\nint n = " + n + ", c = 0;\nwhile (n > 1) {\n    n /= 2;\n    c++;\n}"
                    + "\nSystem.out.println(c);", c, m);
        });
        // ---- 20. Fibonacci
        MCQ_TEMPLATES.add((r, m) -> {
            int n = rnd(r, 5, 20);
            long a = 1, b = 1;
            for (int i = 3; i <= n; i++) { long t = a + b; a = b; b = t; }
            return numeric(r, "In the Fibonacci sequence 1, 1, 2, 3, 5, 8, ... what is term number " + n + "?",
                    n <= 2 ? 1 : b, m);
        });
        // ---- 21. GCD
        MCQ_TEMPLATES.add((r, m) -> {
            int a = rnd(r, 12, 150), b = rnd(r, 12, 150);
            return numeric(r, "What is the greatest common divisor (GCD) of " + a + " and " + b + "?", gcd(a, b), m);
        });
        // ---- 22. prime check
        MCQ_TEMPLATES.add((r, m) -> {
            int n = rnd(r, 10, 300);
            return choice(r, "Is " + n + " a prime number?", isPrime(n) ? "Yes" : "No",
                    Arrays.asList(isPrime(n) ? "No" : "Yes", "Only if it is odd", "Cannot be determined"), m);
        });
        // ---- 23. char arithmetic
        MCQ_TEMPLATES.add((r, m) -> {
            char start = (char) ('A' + r.nextInt(20));
            int k = rnd(r, 1, 5);
            char res = (char) (start + k);
            return choice(r, "What is the output?\n\nchar c = '" + start + "';\nc += " + k + ";\nSystem.out.println(c);",
                    String.valueOf(res),
                    Arrays.asList(String.valueOf((char) (res + 1)), String.valueOf((char) (res - 1)),
                            String.valueOf((char) (res + 2)), String.valueOf(start + k)), m);
        });
        // ---- 24. best data type
        MCQ_TEMPLATES.add((r, m) -> {
            String[][] types = {{"a whole number such as 25", "int"}, {"a decimal number such as 3.14", "double"},
                {"a single character such as 'A'", "char"}, {"a true or false value", "boolean"},
                {"a name or any sequence of characters", "String"}, {"a very large whole number", "long"},
                {"a decimal number with less precision", "float"}};
            String[] t = pick(r, types);
            List<String> wrong = new ArrayList<>();
            for (String[] o : types) if (!o[1].equals(t[1])) wrong.add(o[1]);
            return choice(r, "Which data type is best to store " + t[0] + "?", t[1], wrong, m);
        });
        // ---- 25. OOP / Java / C++ concepts
        MCQ_TEMPLATES.add((r, m) -> {
            String[] c = pick(r, CONCEPTS);
            return choice(r, c[0], c[1], Arrays.asList(c[2], c[3], c[4]), m);
        });

        // =============================== written / coding templates
        final String[] classNames = {"Student", "Employee", "Book", "Car", "Product", "Account", "Teacher",
            "Player", "Course", "Animal", "Customer", "Hotel"};
        final String[] fields = {"id", "age", "price", "quantity", "salary", "rating", "year", "level", "weight"};
        final String[][] pairs = {{"Animal", "Dog"}, {"Shape", "Circle"}, {"Vehicle", "Bus"}, {"Person", "Teacher"},
            {"Employee", "Manager"}, {"Account", "SavingsAccount"}, {"Device", "Laptop"}, {"Bird", "Sparrow"}};
        final String[] methods = {"display", "describe", "calculate", "showDetails", "work", "move"};
        final String[] exceptions = {"InvalidAgeException", "LowBalanceException", "NegativeValueException",
            "StockOutException", "InvalidMarksException"};
        final String[] sorts = {"bubble", "selection", "insertion"};
        final String[] ifaces = {"Printable", "Payable", "Drawable", "Playable", "Resizable"};

        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that reads an integer N and prints the sum of "
                + "all integers from 1 to N. Run it for N = " + rnd(r, 5, 200) + " and show the output.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that prints the multiplication table of "
                + rnd(r, 2, 99) + " from 1 to 10.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a method that checks whether a number is prime, and use "
                + "it to print all prime numbers up to " + rnd(r, 20, 300) + ".", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that prints the first " + rnd(r, 8, 40)
                + " numbers of the Fibonacci series.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that reverses the string \"" + pick(r, WORDS)
                + "\" without using a library reverse method.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that counts the vowels in the string \""
                + pick(r, WORDS) + "\" and prints the count.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a recursive method to calculate the factorial of "
                + rnd(r, 4, 12) + " and print the result.", m, null));
        CODING_TEMPLATES.add((r, m) -> {
            String c = pick(r, classNames), f1 = pick(r, fields), f2 = pick(r, fields);
            if (f1.equals(f2)) f2 = "name";
            return Question.coding("Create a class " + c + " with private fields name (String), " + f1 + " and " + f2
                    + ". Add a constructor, getters and a displayInfo() method. In main, create two " + c
                    + " objects and print their information.", m, null);
        });
        CODING_TEMPLATES.add((r, m) -> {
            String[] p = pick(r, pairs);
            return Question.coding("Create a base class " + p[0] + " and a subclass " + p[1] + " that extends it. "
                    + "Override the method " + pick(r, methods) + "() in the subclass and call it through a "
                    + p[0] + " reference to show polymorphism.", m, null);
        });
        CODING_TEMPLATES.add((r, m) -> Question.coding("Create a custom exception " + pick(r, exceptions)
                + " that is thrown when a value is less than " + rnd(r, 1, 100)
                + ". Throw it from a method and handle it in main with try-catch.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that stores " + rnd(r, 5, 30)
                + " integers in an array, then prints the sum, average, minimum and maximum.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that sorts an array of " + rnd(r, 5, 25)
                + " integers using " + pick(r, sorts) + " sort. Print the array before and after sorting.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that prints a right-angled triangle star "
                + "pattern with " + rnd(r, 3, 15) + " rows.", m, null));
        CODING_TEMPLATES.add((r, m) -> {
            String i = pick(r, ifaces), method = pick(r, methods);
            return Question.coding("Define an interface " + i + " with a method " + method + "(). Implement it in two "
                    + "different classes and call both implementations from main.", m, null);
        });
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that checks whether the number "
                + rnd(r, 100, 99999) + " is a palindrome.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that finds the sum of the digits of the number "
                + rnd(r, 100, 999999) + ".", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that finds the GCD and LCM of " + rnd(r, 12, 150)
                + " and " + rnd(r, 12, 150) + ".", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that checks whether the year "
                + rnd(r, 1900, 2100) + " is a leap year.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that converts " + rnd(r, -20, 120)
                + " degrees Celsius to Fahrenheit using F = C * 9 / 5 + 32.", m, null));
        CODING_TEMPLATES.add((r, m) -> Question.coding("Write a program that reads " + rnd(r, 5, 20) + " numbers and "
                + "prints how many of them are positive, negative and zero.", m, null));
    }

    /**
     * Automatically generates questions (random numbers, words and names inside fixed patterns).
     * All answers are calculated by the program, so every generated MCQ has exactly one correct option.
     * Duplicate questions are never produced.
     *
     * @param count       how many questions to create (for example 500)
     * @param includeMcq  create MCQ questions
     * @param includeCoding create written / coding questions
     * @param mcqMarks    marks of each MCQ
     * @param codingMarks marks of each written / coding question
     * @return the new questions (fewer than count only if the patterns ran out of new combinations)
     */
    public List<Question> generateQuestions(int count, boolean includeMcq, boolean includeCoding,
                                            int mcqMarks, int codingMarks) {
        List<Question> result = new ArrayList<>();
        if (count <= 0 || (!includeMcq && !includeCoding)) return result;
        int codingCount = !includeCoding ? 0 : (!includeMcq ? count : Math.max(1, count / 5));
        int mcqCount = count - codingCount;
        Random random = new Random();
        generate(random, MCQ_TEMPLATES, mcqCount, mcqMarks, result);
        generate(random, CODING_TEMPLATES, codingCount, codingMarks, result);
        return result;
    }

    private static void generate(Random random, List<Template> templates, int target, int marks,
                                 List<Question> out) {
        Set<String> seen = new HashSet<>();
        int made = 0, attempts = 0;
        while (made < target && attempts < target * 80 + 200) {
            attempts++;
            Question q = templates.get(random.nextInt(templates.size())).make(random, marks);
            if (seen.add(q.getText())) {
                out.add(q);
                made++;
            }
        }
    }

    /**
     * Generates a chosen number of MCQ and of written / coding questions at once
     * (for example 800 + 200 = 1000), optionally mixed in random order.
     */
    public List<Question> generateQuestions(int mcqCount, int codingCount, int mcqMarks, int codingMarks,
                                            boolean shuffle) {
        List<Question> result = new ArrayList<>();
        Random random = new Random();
        generate(random, MCQ_TEMPLATES, mcqCount, mcqMarks, result);
        generate(random, CODING_TEMPLATES, codingCount, codingMarks, result);
        if (shuffle) Collections.shuffle(result, random);
        return result;
    }

    /**
     * Generates a chosen number of MCQ, written and coding questions at once
     * (for example 600 + 200 + 200 = 1000), optionally mixed in random order.
     */
    public List<Question> generateQuestions(int mcqCount, int writtenCount, int codingCount,
                                            int mcqMarks, int writtenMarks, int codingMarks, boolean shuffle) {
        List<Question> result = new ArrayList<>();
        Random random = new Random();
        generate(random, MCQ_TEMPLATES, mcqCount, mcqMarks, result);
        generate(random, WRITTEN_TEMPLATES, writtenCount, writtenMarks, result);
        generate(random, CODING_TEMPLATES, codingCount, codingMarks, result);
        if (shuffle) Collections.shuffle(result, random);
        return result;
    }

    // ================================================ import / export of questions
    /** Questions read from a file, plus a description of every item that had to be skipped. */
    public static class ImportResult {
        private final List<Question> questions = new ArrayList<>();
        private final List<String> problems = new ArrayList<>();

        public List<Question> getQuestions() { return questions; }
        public List<String> getProblems() { return problems; }
    }

    /** A question while it is being read from a text file. */
    private static class Draft {
        final Question.Type type;
        final int line;
        final StringBuilder text = new StringBuilder();
        final List<String> options = new ArrayList<>();
        int correct = -1;
        int marks = -1;
        String answer;

        Draft(Question.Type type, int line) {
            this.type = type;
            this.line = line;
        }
    }

    private static final Pattern Q_START = Pattern.compile(
            "^Q(?:uestion)?\\s*\\d*\\s*[:.)\\-]\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern N_START = Pattern.compile("^\\d{1,4}\\s*[.)]\\s+(.*)$");
    private static final Pattern W_START = Pattern.compile(
            "^(?:W|Written)\\s*\\d*\\s*[:.)\\-]\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    // a bare "C" must be followed by ":" because "C)" is an option of an MCQ
    private static final Pattern C_START = Pattern.compile(
            "^(?:(?:Code|Coding)\\s*\\d*\\s*[:.)\\-]|C\\s*\\d*\\s*:)\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPTION = Pattern.compile("^\\(?([A-Ja-j])[.)]\\s*(.+)$");
    private static final Pattern ANSWER = Pattern.compile(
            "^(?:Answer|Ans|Correct(?: answer)?)\\s*[:=\\-]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKS = Pattern.compile(
            "^(?:Marks?|Points?)\\s*[:=]\\s*(\\d+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CORRECT_MARK = Pattern.compile(
            "\\s*(\\*|\\(correct\\)|\\[correct\\]|\\u2713|\\u2714)\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * Reads questions from a .txt, .csv or .pdf file.
     * @param mcqMarks    marks used for MCQ questions that do not state their own marks
     * @param codingMarks marks used for written / coding questions that do not state their own marks
     */
    public ImportResult importQuestions(File file, int mcqMarks, int codingMarks) throws IOException {
        return importQuestions(file, mcqMarks, Math.max(1, codingMarks / 2), codingMarks);
    }

    /**
     * Reads questions from a .txt, .csv or .pdf file.
     * @param writtenMarks marks used for written questions that do not state their own marks
     */
    public ImportResult importQuestions(File file, int mcqMarks, int writtenMarks, int codingMarks)
            throws IOException {
        String text = repository.readQuestionFile(file);
        ImportResult r = new ImportResult();
        if (file.getName().toLowerCase().endsWith(".csv")) parseCsv(text, mcqMarks, writtenMarks, codingMarks, r);
        else parseText(text, mcqMarks, writtenMarks, codingMarks, r);
        return r;
    }

    private static void parseText(String text, int mcqMarks, int writtenMarks, int codingMarks, ImportResult r) {
        String[] lines = text.split("\\R");
        Draft d = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            Matcher m;
            if ((m = W_START.matcher(line)).matches()) {
                finish(d, mcqMarks, writtenMarks, codingMarks, r);
                d = new Draft(Question.Type.WRITTEN, i + 1);
                d.text.append(m.group(1));
                continue;
            }
            if ((m = C_START.matcher(line)).matches()) {
                finish(d, mcqMarks, writtenMarks, codingMarks, r);
                d = new Draft(Question.Type.CODING, i + 1);
                d.text.append(m.group(1));
                continue;
            }
            if ((m = Q_START.matcher(line)).matches() || (m = N_START.matcher(line)).matches()) {
                finish(d, mcqMarks, writtenMarks, codingMarks, r);
                d = new Draft(Question.Type.MCQ, i + 1);
                d.text.append(m.group(1));
                continue;
            }
            if (d == null) continue; // title or instructions before the first question
            if ((m = MARKS.matcher(line)).matches()) {
                d.marks = Integer.parseInt(m.group(1));
                continue;
            }
            if ((m = ANSWER.matcher(line)).matches()) {
                d.answer = m.group(1).trim();
                continue;
            }
            if (d.type == Question.Type.MCQ && (m = OPTION.matcher(line)).matches()) {
                String opt = m.group(2).trim();
                boolean marked = CORRECT_MARK.matcher(opt).find();
                opt = CORRECT_MARK.matcher(opt).replaceAll("").trim();
                d.options.add(opt);
                if (marked) d.correct = d.options.size() - 1;
                continue;
            }
            if (d.type == Question.Type.MCQ && !d.options.isEmpty()) {
                int last = d.options.size() - 1;
                d.options.set(last, d.options.get(last) + " " + line);
            } else {
                d.text.append('\n').append(line);
            }
        }
        finish(d, mcqMarks, writtenMarks, codingMarks, r);
    }

    private static void finish(Draft d, int mcqMarks, int writtenMarks, int codingMarks, ImportResult r) {
        if (d == null) return;
        String q = d.text.toString().trim();
        String where = "Line " + d.line + ": ";
        if (q.isEmpty()) {
            r.problems.add(where + "empty question text");
            return;
        }
        if (d.type != Question.Type.MCQ || d.options.isEmpty()) {
            // a numbered item without options is a normal written question
            if (d.type == Question.Type.CODING) {
                r.questions.add(Question.coding(q, d.marks > 0 ? d.marks : codingMarks, null));
            } else {
                r.questions.add(Question.written(q, d.marks > 0 ? d.marks : writtenMarks, null));
            }
            return;
        }
        String preview = q.replace('\n', ' ');
        if (preview.length() > 40) preview = preview.substring(0, 40) + "...";
        if (d.options.size() < 2) {
            r.problems.add(where + "\"" + preview + "\" needs at least two options");
            return;
        }
        int correct = d.correct;
        int fromAnswer = d.answer == null ? -1 : resolveAnswer(d.answer, d.options);
        if (fromAnswer >= 0) correct = fromAnswer;
        if (correct < 0) {
            r.problems.add(where + "\"" + preview + "\" has no correct answer (add \"Answer: B\" or put * after the option)");
            return;
        }
        r.questions.add(Question.mcq(q, d.options, correct, d.marks > 0 ? d.marks : mcqMarks));
    }

    /** Accepts "B", "b)", "B. text", "2" (option number) or the text of the option itself. */
    private static int resolveAnswer(String value, List<String> options) {
        String v = value.trim();
        if (v.isEmpty()) return -1;
        char c = Character.toUpperCase(v.charAt(0));
        boolean letterOnly = v.length() == 1 || ")".indexOf(v.charAt(1)) >= 0 || ".:".indexOf(v.charAt(1)) >= 0
                || Character.isWhitespace(v.charAt(1));
        if (c >= 'A' && c <= 'J' && letterOnly) {
            int idx = c - 'A';
            return idx < options.size() ? idx : -1;
        }
        try {
            int n = Integer.parseInt(v);
            if (n >= 1 && n <= options.size()) return n - 1;
        } catch (NumberFormatException ignored) { }
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(v)) return i;
        }
        return -1;
    }

    private static void parseCsv(String text, int mcqMarks, int writtenMarks, int codingMarks, ImportResult r) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        int nl = text.indexOf('\n');
        String first = nl < 0 ? text : text.substring(0, nl);
        char delim = ',';
        int best = count(first, ',');
        if (count(first, ';') > best) { delim = ';'; best = count(first, ';'); }
        if (count(first, '\t') > best) delim = '\t';

        List<List<String>> rows = csvRows(text, delim);
        int start = 0;
        if (!rows.isEmpty() && cell(rows.get(0), 0).equalsIgnoreCase("type")) start = 1;
        for (int i = start; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            boolean empty = true;
            for (String c : row) if (!c.trim().isEmpty()) empty = false;
            if (empty) continue;
            String where = "Row " + (i + 1) + ": ";
            String type = cell(row, 0).toUpperCase();
            String q = cell(row, 2);
            int marks = -1;
            try { marks = Integer.parseInt(cell(row, 1)); } catch (NumberFormatException ignored) { }
            if (q.isEmpty()) {
                r.problems.add(where + "empty question text");
                continue;
            }
            if (type.startsWith("W")) {
                r.questions.add(Question.written(q, marks > 0 ? marks : writtenMarks, null));
            } else if (type.startsWith("C")) {
                r.questions.add(Question.coding(q, marks > 0 ? marks : codingMarks, null));
            } else if (type.startsWith("M")) {
                List<String> options = new ArrayList<>();
                for (int c = 3; c <= 12; c++) if (!cell(row, c).isEmpty()) options.add(cell(row, c));
                int correct = resolveAnswer(cell(row, 13), options);
                if (options.size() < 2) r.problems.add(where + "an MCQ needs at least two options");
                else if (correct < 0) r.problems.add(where + "the correct answer column is missing or wrong");
                else r.questions.add(Question.mcq(q, options, correct, marks > 0 ? marks : mcqMarks));
            } else {
                r.problems.add(where + "type must be MCQ, WRITTEN or CODING");
            }
        }
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static String cell(List<String> row, int i) {
        return i < row.size() ? row.get(i).trim() : "";
    }

    /** Splits CSV text into rows and cells (quotes, "" and line breaks inside quotes are supported). */
    private static List<List<String>> csvRows(String text, char delim) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.length() == 0) {
                quoted = true;
            } else if (c == delim) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    /** Writes all questions (with correct answers) to a CSV file that can be imported again. */
    public void exportQuestions(List<Question> questions, File target) throws IOException {
        List<String[]> rows = new ArrayList<>();
        String[] head = new String[14];
        head[0] = "Type";
        head[1] = "Marks";
        head[2] = "Question";
        for (int i = 0; i < 10; i++) head[3 + i] = String.valueOf((char) ('A' + i));
        head[13] = "Correct";
        rows.add(head);
        for (Question q : questions) {
            String[] row = new String[14];
            Arrays.fill(row, "");
            row[0] = q.isMcq() ? "MCQ" : (q.isWritten() ? "WRITTEN" : "CODING");
            row[1] = String.valueOf(q.getMarks());
            row[2] = q.getText();
            if (q.isMcq()) {
                for (int i = 0; i < q.getOptions().size() && i < 10; i++) row[3 + i] = q.getOptions().get(i);
                row[13] = String.valueOf((char) ('A' + q.getCorrectIndex()));
            }
            rows.add(row);
        }
        repository.writeCsv(target, rows);
    }

    /** Writes an example file in the .txt or .csv question format (chosen by the file extension). */
    public void writeSampleFile(File target) throws IOException {
        repository.writeText(target, target.getName().toLowerCase().endsWith(".csv") ? sampleCsv() : sampleText());
    }

    public static String sampleText() {
        return "# Question file for Smart Assessment (txt / pdf).  Lines starting with # are ignored.\n"
             + "# MCQ: start with Q:  then options A) B) C) D)  and  Answer: <letter>  (or put * after the right option)\n"
             + "# Written (typed text answer): start with W:      Coding (code editor): start with C:\n"
             + "# Marks are optional for every question:  Marks: 5\n\n"
             + "Q: Which keyword is used to inherit a class in Java?\n"
             + "A) implements\nB) extends\nC) inherits\nD) super\nAnswer: B\nMarks: 1\n\n"
             + "Q: What is the output of System.out.println(7 / 2);\n"
             + "A) 3.5\nB) 4\nC) 3 *\nD) 3.0\n\n"
             + "Q: Which OOP concept hides the internal details of a class?\n"
             + "A) Abstraction\nB) Recursion\nC) Compilation\nD) Overloading\nAnswer: A\n\n"
             + "W: Define polymorphism and explain it with a suitable example.\nMarks: 5\n\n"
             + "W: Differentiate between an abstract class and an interface.\nMarks: 5\n\n"
             + "C: Write a program that prints the sum of the numbers from 1 to 100.\nMarks: 10\n\n"
             + "C: Create a class Account with a deposit() and a withdraw() method. Throw a custom\n"
             + "exception when the balance is not enough.\nMarks: 15\n";
    }

    public static String sampleCsv() {
        return "Type,Marks,Question,A,B,C,D,E,F,G,H,I,J,Correct\n"
             + "MCQ,1,Which keyword is used to inherit a class in Java?,implements,extends,inherits,super,,,,,,,B\n"
             + "MCQ,1,\"What is the output of 7 / 2 (int division)?\",3.5,4,3,3.0,,,,,,,C\n"
             + "MCQ,2,Which concept hides the internal details of a class?,Abstraction,Recursion,Compilation,Overloading,,,,,,,A\n"
             + "WRITTEN,5,Define polymorphism and explain it with a suitable example.,,,,,,,,,,,\n"
             + "WRITTEN,5,Differentiate between an abstract class and an interface.,,,,,,,,,,,\n"
             + "CODING,10,Write a program that prints the sum of the numbers from 1 to 100.,,,,,,,,,,,\n"
             + "CODING,15,\"Create a class Account with deposit() and withdraw().\nThrow a custom exception when the balance is not enough.\",,,,,,,,,,,\n";
    }
}
