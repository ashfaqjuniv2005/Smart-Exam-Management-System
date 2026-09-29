package smartassessment.model;

import java.io.Serializable;

/** Sent by the server after a successful authentication. */
public class ExamPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Exam exam;
    private final long remainingSeconds;
    private final String restoredCode;
    private final String restoredLanguage;
    private final int[] restoredMcqAnswers;

    public ExamPacket(Exam exam, long remainingSeconds, String restoredCode, String restoredLanguage) {
        this(exam, remainingSeconds, restoredCode, restoredLanguage, null);
    }

    public ExamPacket(Exam exam, long remainingSeconds, String restoredCode, String restoredLanguage,
                      int[] restoredMcqAnswers) {
        this.restoredMcqAnswers = restoredMcqAnswers;
        this.exam = exam;
        this.remainingSeconds = remainingSeconds;
        this.restoredCode = restoredCode;
        this.restoredLanguage = restoredLanguage;
    }

    public Exam getExam() { return exam; }
    public long getRemainingSeconds() { return remainingSeconds; }
    public String getRestoredCode() { return restoredCode; }
    public String getRestoredLanguage() { return restoredLanguage; }
    public int[] getRestoredMcqAnswers() { return restoredMcqAnswers; }
}
