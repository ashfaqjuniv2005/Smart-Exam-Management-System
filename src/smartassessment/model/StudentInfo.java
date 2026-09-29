package smartassessment.model;

import java.io.Serializable;

/** Identity typed by the student in the authentication screen. */
public class StudentInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String batch;
    private final String roll;
    private final String examCode;

    public StudentInfo(String name, String batch, String roll, String examCode) {
        this.name = name;
        this.batch = batch;
        this.roll = roll;
        this.examCode = examCode;
    }

    public String getName() { return name; }
    public String getBatch() { return batch; }
    public String getRoll() { return roll; }
    public String getExamCode() { return examCode; }

    @Override
    public String toString() { return name + " (" + roll + ")"; }
}
