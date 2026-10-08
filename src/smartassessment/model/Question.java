package smartassessment.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * One question of an exam. It is one of three kinds:
 * MCQ (choose an option), Written (typed text answer) or Coding (answered in the code editor).
 */
public class Question implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        MCQ("MCQ"),
        WRITTEN("Written"),
        CODING("Coding");

        private final String label;

        Type(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    private final Type type;
    private final String text;
    private final int marks;
    private final List<String> options;   // MCQ only
    private final int correctIndex;       // MCQ only (-1 when hidden from students)
    private final byte[] image;           // optional picture (e.g. UML diagram)

    private Question(Type type, String text, int marks, List<String> options, int correctIndex, byte[] image) {
        this.type = type;
        this.text = text;
        this.marks = marks;
        this.options = new ArrayList<>(options);
        this.correctIndex = correctIndex;
        this.image = image;
    }

    public static Question mcq(String text, List<String> options, int correctIndex, int marks) {
        return new Question(Type.MCQ, text, marks, options, correctIndex, null);
    }

    public static Question written(String text, int marks, byte[] image) {
        return new Question(Type.WRITTEN, text, marks, new ArrayList<String>(), -1, image);
    }

    public static Question coding(String text, int marks, byte[] image) {
        return new Question(Type.CODING, text, marks, new ArrayList<String>(), -1, image);
    }

    public Type getType() { return type; }
    public boolean isMcq() { return type == Type.MCQ; }
    public boolean isWritten() { return type == Type.WRITTEN; }
    public boolean isCoding() { return type == Type.CODING; }
    public String getText() { return text; }
    public int getMarks() { return marks; }
    public List<String> getOptions() { return options; }
    public int getCorrectIndex() { return correctIndex; }
    public byte[] getImage() { return image; }

    /** Copy sent to students: the correct answer is removed. */
    public Question copyForStudent() {
        return new Question(type, text, marks, options, -1, image);
    }

    @Override
    public String toString() {
        String t = text.replace('\n', ' ');
        if (t.length() > 55) t = t.substring(0, 55) + "...";
        return "[" + type.getLabel() + "]  " + t + "   (" + marks + (marks == 1 ? " mark)" : " marks)");
    }
}
