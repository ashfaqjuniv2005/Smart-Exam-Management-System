package smartassessment.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import smartassessment.model.Exam;
import smartassessment.model.Submission;

/**
 * Stores exams and submissions as plain files using Java object serialization.
 * (No SQL / database is used.)
 */
public final class FileStore {
    private static final File BASE = new File(System.getProperty("user.dir"), "SmartExamData");

    private FileStore() { }

    public static File getBaseDir() { return BASE; }

    private static String safe(String s) {
        return s == null ? "unknown" : s.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static File examsDir() {
        File d = new File(BASE, "exams");
        d.mkdirs();
        return d;
    }

    private static File submissionDir(String examCode) {
        File d = new File(new File(BASE, "submissions"), safe(examCode));
        d.mkdirs();
        return d;
    }

    // ---------------------------------------------------------------- exams
    public static void saveExam(Exam exam) throws IOException {
        File f = new File(examsDir(), safe(exam.getCode()) + ".exam");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) {
            out.writeObject(exam);
        }
    }

    public static Exam loadExam(String code) throws IOException, ClassNotFoundException {
        File f = new File(examsDir(), safe(code) + ".exam");
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            return (Exam) in.readObject();
        }
    }

    public static List<String> listExamCodes() {
        List<String> codes = new ArrayList<>();
        File[] files = examsDir().listFiles((d, n) -> n.endsWith(".exam"));
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                codes.add(n.substring(0, n.length() - ".exam".length()));
            }
        }
        Collections.sort(codes);
        return codes;
    }

    // ---------------------------------------------------------- submissions
    public static void saveSubmission(Submission s) throws IOException {
        File dir = submissionDir(s.getExamCode());
        String base = safe(s.getStudent().getRoll());
        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(new File(dir, base + ".sub")))) {
            out.writeObject(s);
        }
        // Readable copy of the final code, so the teacher can also open it in an IDE.
        File codeFile = new File(dir, base + "_" + safe(s.getStudent().getName()) + extensionFor(s.getLanguage()));
        Files.write(codeFile.toPath(), s.getFinalCode().getBytes(StandardCharsets.UTF_8));
    }

    public static List<Submission> loadSubmissions(String examCode) {
        List<Submission> list = new ArrayList<>();
        File[] files = submissionDir(examCode).listFiles((d, n) -> n.endsWith(".sub"));
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
                    list.add((Submission) in.readObject());
                } catch (Exception ex) {
                    System.err.println("Could not read " + f + ": " + ex);
                }
            }
        }
        return list;
    }

    public static String extensionFor(String language) {
        if ("C++".equals(language)) return ".cpp";
        if ("C".equals(language)) return ".c";
        return ".java";
    }

    public static void exportCsv(List<Submission> list, File target) throws IOException {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (PrintWriter pw = new PrintWriter(target, "UTF-8")) {
            pw.println("Roll,Name,Batch,Language,Submitted At,Warnings,Auto Submitted,Collected By Server,MCQ Score,MCQ Total");
            for (Submission s : list) {
                pw.println(csv(s.getStudent().getRoll()) + "," + csv(s.getStudent().getName()) + ","
                        + csv(s.getStudent().getBatch()) + "," + csv(s.getLanguage()) + ","
                        + csv(fmt.format(new Date(s.getSubmittedAt()))) + "," + s.getWarnings() + ","
                        + s.isAutoSubmitted() + "," + s.isCollectedByServer() + ","
                        + (s.getMcqAnswers() == null ? "" : String.valueOf(s.getMcqScore())) + ","
                        + (s.getMcqAnswers() == null ? "" : String.valueOf(s.getMcqTotal())));
            }
        }
    }

    private static String csv(String v) {
        return "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"";
    }
}
