package smartassessment.server;

import java.util.ArrayList;
import java.util.List;
import smartassessment.model.CheatEvent;
import smartassessment.model.StudentInfo;
import smartassessment.model.Snapshot;
import smartassessment.model.Submission;

/** Everything the server knows about one student during an exam. */
public class StudentSession {
    private final StudentInfo info;
    private volatile boolean connected;
    private volatile boolean submitted;
    private volatile int warnings;
    private volatile long lastSeen = System.currentTimeMillis();
    private volatile long lastSnapshotTime;
    private volatile String lastCode = "";
    private volatile String language = "Java";
    private volatile int[] mcqAnswers;
    private final List<Snapshot> snapshots = new ArrayList<>();
    private final List<CheatEvent> events = new ArrayList<>();
    Object handler; // the ClientHandler that currently owns this session

    public StudentSession(StudentInfo info) { this.info = info; }

    public StudentInfo getInfo() { return info; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public boolean isSubmitted() { return submitted; }
    public void setSubmitted(boolean submitted) { this.submitted = submitted; }
    public int getWarnings() { return warnings; }
    public long getLastSeen() { return lastSeen; }
    public void touch() { lastSeen = System.currentTimeMillis(); }
    public long getLastSnapshotTime() { return lastSnapshotTime; }
    public String getLastCode() { return lastCode; }
    public String getLanguage() { return language; }
    public int[] getMcqAnswers() { return mcqAnswers; }
    public void setMcqAnswers(int[] mcqAnswers) { this.mcqAnswers = mcqAnswers; }
    public void setLanguage(String language) { this.language = language; }

    public synchronized void addSnapshot(Snapshot s) {
        lastCode = s.getCode();
        lastSnapshotTime = s.getTimeMillis();
        if (snapshots.isEmpty() || !snapshots.get(snapshots.size() - 1).getCode().equals(s.getCode())
                || s.getNote().length() > 0) {
            snapshots.add(s);
        }
    }

    public synchronized void addEvent(CheatEvent e) {
        events.add(e);
        if (e.isWarning()) warnings++;
    }

    public synchronized List<CheatEvent> getEventsCopy() { return new ArrayList<>(events); }

    /** Builds the final record that is stored on disk. */
    public synchronized Submission buildSubmission(String examCode, String code, String lang,
                                                   boolean auto, boolean byServer) {
        Submission s = new Submission();
        s.setStudent(info);
        s.setExamCode(examCode);
        s.setFinalCode(code);
        s.setLanguage(lang);
        s.setAutoSubmitted(auto);
        s.setCollectedByServer(byServer);
        s.setSubmittedAt(System.currentTimeMillis());
        s.setWarnings(warnings);
        s.setSnapshots(new ArrayList<>(snapshots));
        s.getSnapshots().add(new Snapshot(System.currentTimeMillis(), code, "FINAL"));
        s.setEvents(new ArrayList<>(events));
        return s;
    }
}
