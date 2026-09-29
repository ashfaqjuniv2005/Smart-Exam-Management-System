package smartassessment.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** One multiple-choice question created by the teacher. */
public class McqQuestion implements Serializable {
    private static final long serialVersionUID = 1L;

    private String text;
    private List<String> options;
    private int correctIndex;
    private int marks;

    public McqQuestion(String text, List<String> options, int correctIndex, int marks) {
        this.text = text;
        this.options = new ArrayList<>(options);
        this.correctIndex = correctIndex;
        this.marks = marks;
    }

    public String getText() { return text; }
    public List<String> getOptions() { return options; }
    public int getCorrectIndex() { return correctIndex; }
    public int getMarks() { return marks; }

    /** Copy for students: the correct answer is removed (-1). */
    public McqQuestion copyForStudent() {
        return new McqQuestion(text, options, -1, marks);
    }

    @Override
    public String toString() {
        String t = text.replace('\n', ' ');
        return (t.length() > 45 ? t.substring(0, 45) + "..." : t) + "  [" + marks + " mark" + (marks == 1 ? "" : "s") + "]";
    }
}
