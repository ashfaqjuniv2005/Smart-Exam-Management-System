package smartassessment.network;

import java.io.Serializable;
import smartassessment.model.Exam;

/** Constants, message types and packets shared by the teacher server and the student client. */
public final class Protocol {
    public static final int DEFAULT_PORT = 5555;
    public static final int CONNECT_TIMEOUT_MS = 4000;
    public static final int HANDSHAKE_TIMEOUT_MS = 6000;
    public static final int SNAPSHOT_INTERVAL_MS = 10_000;
    public static final int MONITOR_INTERVAL_MS = 4_000;
    public static final int RECONNECT_INTERVAL_MS = 5_000;

    /** Kinds of messages that travel over the network. */
    public enum Type {
        AUTH_REQUEST, AUTH_OK, AUTH_FAIL,
        SNAPSHOT, CHEAT_EVENT, HEARTBEAT, MCQ_ANSWERS, WRITTEN_ANSWERS,
        SUBMIT, SUBMIT_OK,
        FORCE_SUBMIT, BROADCAST
    }

    /** Sent by the server after a successful login. */
    public static class ExamPacket implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Exam exam;
        private final long remainingSeconds;
        private final String[] restoredCodes;
        private final String[] restoredLanguages;
        private final int[] restoredMcqAnswers;
        private final String[] restoredWrittenAnswers;

        public ExamPacket(Exam exam, long remainingSeconds, String[] restoredCodes,
                          String[] restoredLanguages, int[] restoredMcqAnswers,
                          String[] restoredWrittenAnswers) {
            this.exam = exam;
            this.remainingSeconds = remainingSeconds;
            this.restoredCodes = restoredCodes;
            this.restoredLanguages = restoredLanguages;
            this.restoredMcqAnswers = restoredMcqAnswers;
            this.restoredWrittenAnswers = restoredWrittenAnswers;
        }

        public Exam getExam() { return exam; }
        public long getRemainingSeconds() { return remainingSeconds; }
        public String[] getRestoredCodes() { return restoredCodes; }
        public String[] getRestoredLanguages() { return restoredLanguages; }
        public int[] getRestoredMcqAnswers() { return restoredMcqAnswers; }
        public String[] getRestoredWrittenAnswers() { return restoredWrittenAnswers; }
    }

    private Protocol() { }
}
