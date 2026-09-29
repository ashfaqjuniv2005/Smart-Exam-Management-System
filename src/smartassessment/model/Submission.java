package smartassessment.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Final answer of one student together with the evidence collected. */
public class Submission implements Serializable {
    private static final long serialVersionUID = 1L;

    private StudentInfo student;
    private String examCode = "";
    private String language = "Java";
    private String finalCode = "";
    private long submittedAt;
    private boolean autoSubmitted;
    private boolean collectedByServer;
    private int warnings;
    private List<Snapshot> snapshots = new ArrayList<>();
    private List<CheatEvent> events = new ArrayList<>();
    private int[] mcqAnswers; // chosen option per MCQ (-1 = not answered); null if no MCQ part
    private int mcqScore;
    private int mcqTotal;

    public StudentInfo getStudent() { return student; }
    public void setStudent(StudentInfo student) { this.student = student; }
    public String getExamCode() { return examCode; }
    public void setExamCode(String examCode) { this.examCode = examCode; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getFinalCode() { return finalCode; }
    public void setFinalCode(String finalCode) { this.finalCode = finalCode; }
    public long getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(long submittedAt) { this.submittedAt = submittedAt; }
    public boolean isAutoSubmitted() { return autoSubmitted; }
    public void setAutoSubmitted(boolean autoSubmitted) { this.autoSubmitted = autoSubmitted; }
    public boolean isCollectedByServer() { return collectedByServer; }
    public void setCollectedByServer(boolean collectedByServer) { this.collectedByServer = collectedByServer; }
    public int getWarnings() { return warnings; }
    public void setWarnings(int warnings) { this.warnings = warnings; }
    public List<Snapshot> getSnapshots() { return snapshots; }
    public void setSnapshots(List<Snapshot> snapshots) { this.snapshots = snapshots; }
    public int[] getMcqAnswers() { return mcqAnswers; }
    public void setMcqAnswers(int[] mcqAnswers) { this.mcqAnswers = mcqAnswers; }
    public int getMcqScore() { return mcqScore; }
    public void setMcqScore(int mcqScore) { this.mcqScore = mcqScore; }
    public int getMcqTotal() { return mcqTotal; }
    public void setMcqTotal(int mcqTotal) { this.mcqTotal = mcqTotal; }
    public List<CheatEvent> getEvents() { return events; }
    public void setEvents(List<CheatEvent> events) { this.events = events; }
}
