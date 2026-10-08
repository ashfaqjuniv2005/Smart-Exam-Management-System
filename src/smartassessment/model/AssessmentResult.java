package smartassessment.model;

import java.io.Serializable;

/**
 * Marks of one student: the MCQ part is marked automatically,
 * the written and the coding parts are marked by the teacher.
 */
public class AssessmentResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Student student;
    private final String examCode;
    private int mcqScore;
    private int mcqTotal;
    private int writtenMarks = -1; // -1 = not graded yet
    private int writtenTotal;
    private int codingMarks = -1;  // -1 = not graded yet
    private int codingTotal;
    private int warnings;
    private String remarks = "";

    public AssessmentResult(Student student, String examCode) {
        this.student = student;
        this.examCode = examCode;
    }

    public Student getStudent() { return student; }
    public String getExamCode() { return examCode; }
    public int getMcqScore() { return mcqScore; }
    public void setMcqScore(int mcqScore) { this.mcqScore = mcqScore; }
    public int getMcqTotal() { return mcqTotal; }
    public void setMcqTotal(int mcqTotal) { this.mcqTotal = mcqTotal; }
    public int getWrittenMarks() { return writtenMarks; }
    public void setWrittenMarks(int writtenMarks) { this.writtenMarks = writtenMarks; }
    public int getWrittenTotal() { return writtenTotal; }
    public void setWrittenTotal(int writtenTotal) { this.writtenTotal = writtenTotal; }
    public int getCodingMarks() { return codingMarks; }
    public void setCodingMarks(int codingMarks) { this.codingMarks = codingMarks; }
    public int getCodingTotal() { return codingTotal; }
    public void setCodingTotal(int codingTotal) { this.codingTotal = codingTotal; }
    public int getWarnings() { return warnings; }
    public void setWarnings(int warnings) { this.warnings = warnings; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks == null ? "" : remarks; }

    public boolean isWrittenGraded() { return writtenMarks >= 0; }
    public boolean isCodingGraded() { return codingMarks >= 0; }

    /** MCQ score + the marks the teacher gave for the written / coding part (0 while not graded). */
    public int getTotalScore() { return mcqScore + Math.max(0, writtenMarks) + Math.max(0, codingMarks); }

    public int getTotalMarks() { return mcqTotal + writtenTotal + codingTotal; }

    public String getSummary() {
        return getTotalScore() + " / " + getTotalMarks()
                + ((writtenTotal > 0 && !isWrittenGraded()) || (codingTotal > 0 && !isCodingGraded())
                        ? "  (written / coding not graded)" : "");
    }
}
