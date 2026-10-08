package smartassessment.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import smartassessment.model.Submission.CheatEvent;
import smartassessment.model.Submission.Snapshot;

/** Everything the teacher's server knows about one student while the exam is running. */
public class StudentSession {
    private final Student student;
    private volatile boolean connected;
    private volatile boolean submitted;
    private volatile int warnings;
    private volatile long lastSeen = System.currentTimeMillis();
    private volatile long lastSnapshotTime;
    private volatile int[] mcqAnswers;
    private volatile String[] writtenAnswers;
    private final String[] codes;      // latest code of each coding question
    private final String[] languages;
    private final List<Snapshot> snapshots = new ArrayList<>();
    private final List<CheatEvent> events = new ArrayList<>();

    public StudentSession(Student student, int codingQuestionCount) {
        this.student = student;
        this.codes = new String[codingQuestionCount];
        this.languages = new String[codingQuestionCount];
        Arrays.fill(codes, "");
        Arrays.fill(languages, "Java");
    }

    public Student getStudent() { return student; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public boolean isSubmitted() { return submitted; }
    public void setSubmitted(boolean submitted) { this.submitted = submitted; }
    public int getWarnings() { return warnings; }
    public long getLastSeen() { return lastSeen; }
    public void touch() { lastSeen = System.currentTimeMillis(); }
    public long getLastSnapshotTime() { return lastSnapshotTime; }
    public int[] getMcqAnswers() { return mcqAnswers; }
    public void setMcqAnswers(int[] mcqAnswers) { this.mcqAnswers = mcqAnswers; }
    public String[] getWrittenAnswers() { return writtenAnswers; }
    public void setWrittenAnswers(String[] writtenAnswers) { this.writtenAnswers = writtenAnswers; }
    public synchronized String[] getCodes() { return codes.clone(); }
    public synchronized String[] getLanguages() { return languages.clone(); }

    public synchronized void addSnapshot(Snapshot s) {
        int q = s.getQuestionNo();
        if (q < 0 || q >= codes.length) return;
        codes[q] = s.getCode();
        languages[q] = s.getLanguage();
        lastSnapshotTime = s.getTimeMillis();
        boolean changed = true;
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            if (snapshots.get(i).getQuestionNo() == q) {
                changed = !snapshots.get(i).getCode().equals(s.getCode());
                break;
            }
        }
        if (changed || s.getNote().length() > 0) snapshots.add(s);
    }

    /** Takes over the answers sent with the final Submit message. */
    public synchronized void updateAnswers(List<String> newCodes, List<String> newLanguages, int[] newMcq,
                                           List<String> newWritten) {
        for (int i = 0; i < codes.length; i++) {
            if (newCodes != null && i < newCodes.size()) codes[i] = newCodes.get(i);
            if (newLanguages != null && i < newLanguages.size()) languages[i] = newLanguages.get(i);
        }
        if (newMcq != null) mcqAnswers = newMcq;
        if (newWritten != null) writtenAnswers = newWritten.toArray(new String[0]);
    }

    public synchronized void addEvent(CheatEvent e) {
        events.add(e);
        if (e.isWarning()) warnings++;
    }

    public synchronized List<CheatEvent> getEventsCopy() { return new ArrayList<>(events); }

    /** Builds the final record that is stored on disk. */
    public synchronized Submission buildSubmission(String examCode, boolean auto, boolean byServer) {
        Submission s = new Submission();
        s.setStudent(student);
        s.setExamCode(examCode);
        s.setAutoSubmitted(auto);
        s.setCollectedByServer(byServer);
        s.setSubmittedAt(System.currentTimeMillis());
        s.setWarnings(warnings);
        s.setCodeAnswers(new ArrayList<>(Arrays.asList(codes)));
        s.setLanguages(new ArrayList<>(Arrays.asList(languages)));
        s.setMcqAnswers(mcqAnswers);
        String[] written = writtenAnswers;
        s.setWrittenAnswers(written == null ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(written)));
        s.setSnapshots(new ArrayList<>(snapshots));
        for (int i = 0; i < codes.length; i++) {
            s.getSnapshots().add(new Snapshot(System.currentTimeMillis(), i, languages[i], codes[i], "FINAL"));
        }
        s.setEvents(new ArrayList<>(events));
        return s;
    }
}
