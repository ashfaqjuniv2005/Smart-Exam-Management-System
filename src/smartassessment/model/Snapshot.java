package smartassessment.model;

import java.io.Serializable;

/** A copy of the student's code at a given moment (used for replay). */
public class Snapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long timeMillis;
    private final String code;
    private final String note;

    public Snapshot(long timeMillis, String code, String note) {
        this.timeMillis = timeMillis;
        this.code = code == null ? "" : code;
        this.note = note == null ? "" : note;
    }

    public long getTimeMillis() { return timeMillis; }
    public String getCode() { return code; }
    public String getNote() { return note; }
}
