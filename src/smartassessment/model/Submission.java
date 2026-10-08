package smartassessment.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Final answers of one student together with the evidence collected during the exam. */
public class Submission implements Serializable {
    private static final long serialVersionUID = 1L;

    /** A copy of the student's code for one written/coding question at a given moment (used for replay). */
    public static class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        private final long timeMillis;
        private final int questionNo; // index among the coding questions
        private final String language;
        private final String code;
        private final String note;

        public Snapshot(long timeMillis, int questionNo, String language, String code, String note) {
            this.timeMillis = timeMillis;
            this.questionNo = questionNo;
            this.language = language == null ? "Java" : language;
            this.code = code == null ? "" : code;
            this.note = note == null ? "" : note;
        }

        public long getTimeMillis() { return timeMillis; }
        public int getQuestionNo() { return questionNo; }
        public String getLanguage() { return language; }
        public String getCode() { return code; }
        public String getNote() { return note; }
    }

    /** One suspicious action recorded during the exam. */
    public static class CheatEvent implements Serializable {
        private static final long serialVersionUID = 1L;

        public enum Kind {
            FOCUS_LOST("Window lost focus", true),
            FOCUS_RETURNED("Window focus returned", false),
            PASTE_ATTEMPT("Paste attempt blocked", true),
            COPY_ATTEMPT("Copy/Cut attempt blocked", true),
            LARGE_INSERT("Large text insertion", true),
            MULTI_DISPLAY("Multiple displays detected", true),
            FORBIDDEN_APP("Forbidden application running", true),
            DISCONNECTED("Disconnected from server", false),
            RECONNECTED("Reconnected to server", false);

            private final String label;
            private final boolean warning;

            Kind(String label, boolean warning) {
                this.label = label;
                this.warning = warning;
            }

            public String getLabel() { return label; }
            public boolean isWarning() { return warning; }
        }

        private final long timeMillis;
        private final Kind kind;
        private final String detail;

        public CheatEvent(Kind kind, String detail) {
            this(System.currentTimeMillis(), kind, detail);
        }

        public CheatEvent(long timeMillis, Kind kind, String detail) {
            this.timeMillis = timeMillis;
            this.kind = kind;
            this.detail = detail == null ? "" : detail;
        }

        public long getTimeMillis() { return timeMillis; }
        public Kind getKind() { return kind; }
        public String getDetail() { return detail; }
        public boolean isWarning() { return kind.isWarning(); }
    }

    private Student student;
    private String examCode = "";
    private long submittedAt;
    private boolean autoSubmitted;
    private boolean collectedByServer;
    private int warnings;
    private List<String> writtenAnswers = new ArrayList<>(); // one per written question (typed text)
    private List<String> codeAnswers = new ArrayList<>();    // one per coding question
    private List<String> languages = new ArrayList<>();   // language of each code answer
    private int[] mcqAnswers;                              // chosen option per MCQ, -1 = none (null if no MCQ)
    private List<Snapshot> snapshots = new ArrayList<>();
    private List<CheatEvent> events = new ArrayList<>();

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public String getExamCode() { return examCode; }
    public void setExamCode(String examCode) { this.examCode = examCode; }
    public long getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(long submittedAt) { this.submittedAt = submittedAt; }
    public boolean isAutoSubmitted() { return autoSubmitted; }
    public void setAutoSubmitted(boolean autoSubmitted) { this.autoSubmitted = autoSubmitted; }
    public boolean isCollectedByServer() { return collectedByServer; }
    public void setCollectedByServer(boolean collectedByServer) { this.collectedByServer = collectedByServer; }
    public int getWarnings() { return warnings; }
    public void setWarnings(int warnings) { this.warnings = warnings; }
    public List<String> getWrittenAnswers() { return writtenAnswers; }
    public void setWrittenAnswers(List<String> writtenAnswers) { this.writtenAnswers = writtenAnswers; }
    public List<String> getCodeAnswers() { return codeAnswers; }
    public void setCodeAnswers(List<String> codeAnswers) { this.codeAnswers = codeAnswers; }
    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }
    public int[] getMcqAnswers() { return mcqAnswers; }
    public void setMcqAnswers(int[] mcqAnswers) { this.mcqAnswers = mcqAnswers; }
    public List<Snapshot> getSnapshots() { return snapshots; }
    public void setSnapshots(List<Snapshot> snapshots) { this.snapshots = snapshots; }
    public List<CheatEvent> getEvents() { return events; }
    public void setEvents(List<CheatEvent> events) { this.events = events; }
}
