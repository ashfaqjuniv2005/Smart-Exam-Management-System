package smartassessment.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exam created by the teacher: settings plus a list of MCQ, written and coding questions. */
public class Exam implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final List<String> DEFAULT_FORBIDDEN_APPS = Arrays.asList(
            "chrome", "firefox", "msedge", "brave", "opera", "vivaldi", "safari",
            "discord", "telegram", "whatsapp", "teams", "zoom", "skype",
            "anydesk", "teamviewer", "code", "cursor", "chatgpt", "claude");

    private String title = "";
    private String code = "";
    private int durationMinutes = 35;
    private int maxWarnings = 0; // 0 = unlimited
    private boolean blockMultiDisplay = true;
    private List<String> allowedRolls = new ArrayList<>();
    private List<String> forbiddenApps = new ArrayList<>(DEFAULT_FORBIDDEN_APPS);
    private List<Question> questions = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public int getMaxWarnings() { return maxWarnings; }
    public void setMaxWarnings(int maxWarnings) { this.maxWarnings = maxWarnings; }
    public boolean isBlockMultiDisplay() { return blockMultiDisplay; }
    public void setBlockMultiDisplay(boolean blockMultiDisplay) { this.blockMultiDisplay = blockMultiDisplay; }
    public List<String> getAllowedRolls() { return allowedRolls; }
    public void setAllowedRolls(List<String> allowedRolls) { this.allowedRolls = allowedRolls; }
    public List<String> getForbiddenApps() { return forbiddenApps; }
    public void setForbiddenApps(List<String> forbiddenApps) { this.forbiddenApps = forbiddenApps; }
    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }

    // ------------------------------------------------------------ questions
    public List<Question> getMcqQuestions() {
        List<Question> list = new ArrayList<>();
        for (Question q : questions) if (q.isMcq()) list.add(q);
        return list;
    }

    public List<Question> getWrittenQuestions() {
        List<Question> list = new ArrayList<>();
        for (Question q : questions) if (q.isWritten()) list.add(q);
        return list;
    }

    public List<Question> getCodingQuestions() {
        List<Question> list = new ArrayList<>();
        for (Question q : questions) if (q.isCoding()) list.add(q);
        return list;
    }

    public boolean hasMcq() { return !getMcqQuestions().isEmpty(); }
    public boolean hasWritten() { return !getWrittenQuestions().isEmpty(); }
    public boolean hasCoding() { return !getCodingQuestions().isEmpty(); }

    public int getMcqTotalMarks() {
        int total = 0;
        for (Question q : getMcqQuestions()) total += q.getMarks();
        return total;
    }

    public int getWrittenTotalMarks() {
        int total = 0;
        for (Question q : getWrittenQuestions()) total += q.getMarks();
        return total;
    }

    public int getCodingTotalMarks() {
        int total = 0;
        for (Question q : getCodingQuestions()) total += q.getMarks();
        return total;
    }

    public int getTotalMarks() { return getMcqTotalMarks() + getWrittenTotalMarks() + getCodingTotalMarks(); }

    /**
     * Marks earned in the MCQ part.
     * @param answers chosen option per MCQ (in the order of getMcqQuestions()), -1 = not answered
     */
    public int scoreMcq(int[] answers) {
        int score = 0;
        List<Question> mcq = getMcqQuestions();
        for (int i = 0; i < mcq.size() && answers != null && i < answers.length; i++) {
            if (answers[i] == mcq.get(i).getCorrectIndex()) score += mcq.get(i).getMarks();
        }
        return score;
    }

    // --------------------------------------------------------------- access
    /** Returns true if the roll may sit this exam (empty list = everybody). */
    public boolean isRollAllowed(String roll) {
        if (allowedRolls == null || allowedRolls.isEmpty()) return true;
        for (String r : allowedRolls) {
            if (r.trim().equalsIgnoreCase(roll.trim())) return true;
        }
        return false;
    }

    /** Copy sent to students (no allowed-roll list, no correct MCQ answers). */
    public Exam copyForStudent() {
        Exam e = new Exam();
        e.title = title;
        e.code = code;
        e.durationMinutes = durationMinutes;
        e.maxWarnings = maxWarnings;
        e.blockMultiDisplay = blockMultiDisplay;
        e.forbiddenApps = new ArrayList<>(forbiddenApps);
        for (Question q : questions) e.questions.add(q.copyForStudent());
        return e;
    }

    @Override
    public String toString() { return code + " - " + title; }
}
