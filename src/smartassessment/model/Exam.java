package smartassessment.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exam configuration created by the teacher. */
public class Exam implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final List<String> DEFAULT_FORBIDDEN_APPS = Arrays.asList(
            "chrome", "firefox", "msedge", "brave", "opera", "vivaldi", "safari",
            "discord", "telegram", "whatsapp", "teams", "zoom", "skype",
            "anydesk", "teamviewer", "code", "cursor", "chatgpt", "claude");

    private String title = "";
    private String code = "";
    private String questionText = "";
    private int durationMinutes = 35;
    private int totalMarks = 20;
    private int maxWarnings = 0; // 0 = unlimited
    private boolean blockMultiDisplay = true;
    private List<String> allowedRolls = new ArrayList<>();
    private List<String> forbiddenApps = new ArrayList<>(DEFAULT_FORBIDDEN_APPS);
    private byte[] questionImage;
    public static final String TYPE_CODING = "CODING";
    public static final String TYPE_MCQ = "MCQ";
    public static final String TYPE_BOTH = "BOTH";
    private String examType = TYPE_CODING;
    private List<McqQuestion> mcqQuestions = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }
    public int getMaxWarnings() { return maxWarnings; }
    public void setMaxWarnings(int maxWarnings) { this.maxWarnings = maxWarnings; }
    public boolean isBlockMultiDisplay() { return blockMultiDisplay; }
    public void setBlockMultiDisplay(boolean blockMultiDisplay) { this.blockMultiDisplay = blockMultiDisplay; }
    public List<String> getAllowedRolls() { return allowedRolls; }
    public void setAllowedRolls(List<String> allowedRolls) { this.allowedRolls = allowedRolls; }
    public List<String> getForbiddenApps() { return forbiddenApps; }
    public void setForbiddenApps(List<String> forbiddenApps) { this.forbiddenApps = forbiddenApps; }
    public byte[] getQuestionImage() { return questionImage; }
    public void setQuestionImage(byte[] questionImage) { this.questionImage = questionImage; }

    public String getExamType() { return examType == null ? TYPE_CODING : examType; }
    public void setExamType(String examType) { this.examType = examType; }
    public List<McqQuestion> getMcqQuestions() {
        if (mcqQuestions == null) mcqQuestions = new ArrayList<>();
        return mcqQuestions;
    }
    public void setMcqQuestions(List<McqQuestion> mcqQuestions) { this.mcqQuestions = mcqQuestions; }

    /** True when the exam has a written / coding part. */
    public boolean hasCoding() { return !TYPE_MCQ.equals(getExamType()); }

    /** True when the exam has an MCQ part (with at least one question). */
    public boolean hasMcq() { return !TYPE_CODING.equals(getExamType()) && !getMcqQuestions().isEmpty(); }

    public int getMcqTotalMarks() {
        int total = 0;
        for (McqQuestion q : getMcqQuestions()) total += q.getMarks();
        return total;
    }

    /** Marks earned for the given answers (index of chosen option per question, -1 = not answered). */
    public int scoreMcq(int[] answers) {
        int score = 0;
        List<McqQuestion> list = getMcqQuestions();
        for (int i = 0; i < list.size() && answers != null && i < answers.length; i++) {
            if (answers[i] == list.get(i).getCorrectIndex()) score += list.get(i).getMarks();
        }
        return score;
    }

    /** Returns true if the roll may sit this exam (empty list = everybody). */
    public boolean isRollAllowed(String roll) {
        if (allowedRolls == null || allowedRolls.isEmpty()) return true;
        for (String r : allowedRolls) {
            if (r.trim().equalsIgnoreCase(roll.trim())) return true;
        }
        return false;
    }

    /** Copy sent to students (the allowed-roll list is not disclosed). */
    public Exam copyForStudent() {
        Exam e = new Exam();
        e.title = title;
        e.code = code;
        e.questionText = questionText;
        e.durationMinutes = durationMinutes;
        e.totalMarks = totalMarks;
        e.maxWarnings = maxWarnings;
        e.blockMultiDisplay = blockMultiDisplay;
        e.forbiddenApps = new ArrayList<>(forbiddenApps);
        e.questionImage = questionImage;
        e.examType = getExamType();
        e.mcqQuestions = new ArrayList<>();
        for (McqQuestion q : getMcqQuestions()) e.mcqQuestions.add(q.copyForStudent());
        return e;
    }

    @Override
    public String toString() { return code + " - " + title; }
}
