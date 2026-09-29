package smartassessment.model;

import java.io.Serializable;

/** One suspicious action recorded during the exam. */
public class CheatEvent implements Serializable {
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
