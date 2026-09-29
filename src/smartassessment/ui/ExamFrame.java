package smartassessment.ui;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.PrintWriter;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import smartassessment.client.ExamClient;
import smartassessment.model.*;
import smartassessment.util.AntiCheat;
import smartassessment.util.CodeRunner;

/** The student's exam portal: question viewer + code editor + timer + anti-cheat monitoring. */
public class ExamFrame extends JFrame implements ExamClient.Listener {
    private static final long serialVersionUID = 1L;

    private static final String JAVA_TEMPLATE =
            "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n"
            + "        \n    }\n}\n";
    private static final String CPP_TEMPLATE =
            "#include <iostream>\nusing namespace std;\n\nint main() {\n    \n    return 0;\n}\n";
    private static final String C_TEMPLATE =
            "#include <stdio.h>\n\nint main() {\n    \n    return 0;\n}\n";

    private final ExamClient client;
    private final Exam exam;
    private final StudentInfo student;

    private final CodeEditorPane editor = new CodeEditorPane();
    private final JTextArea outputArea = new JTextArea();
    private final JTextArea inputArea = new JTextArea();
    private final JComboBox<String> languageBox = new JComboBox<>(new String[]{"Java", "C++", "C"});
    private final JButton runButton = new JButton("\u25B6 Run");
    private final JButton submitButton = new JButton("Submit");
    private final JLabel timerLabel = new JLabel("00:00", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Connected");
    private final JLabel warningLabel = new JLabel("Warnings: 0");
    private final JLabel bannerLabel = new JLabel(" ");
    private McqPanel mcqPanel; // null when the exam has no MCQ part

    private final Timer countdownTimer;
    private final Timer snapshotTimer;
    private final Timer monitorTimer;
    private final Timer reconnectTimer;

    private long deadlineNanos;
    private String lastSentCode = "";
    private String previousLanguage = "Java";
    private long focusLostAt;
    private int warnings;
    private volatile boolean submitting;
    private volatile boolean finished;
    private volatile CountDownLatch ackLatch;
    private int lastDisplayCount = 1;
    private final Set<String> reportedApps = new TreeSet<>();
    private boolean reconnecting;

    public ExamFrame(ExamClient client, ExamPacket packet, String name, String batch, String roll) {
        super("Smart Assessment - Exam Portal");
        this.client = client;
        this.exam = packet.getExam();
        this.student = client.getInfo();
        client.setListener(this);

        setUndecorated(true);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        buildUi(name, batch, roll);
        if (mcqPanel != null) mcqPanel.setAnswers(packet.getRestoredMcqAnswers());

        // restore code after a reconnect, otherwise start from a template
        String lang = packet.getRestoredLanguage() == null ? "Java" : packet.getRestoredLanguage();
        languageBox.setSelectedItem(lang);
        previousLanguage = lang;
        editor.setLanguage(lang);
        String restored = packet.getRestoredCode();
        editor.setCodeSilently(restored != null && !restored.isEmpty() ? restored : templateFor(lang));

        deadlineNanos = System.nanoTime() + packet.getRemainingSeconds() * 1_000_000_000L;

        countdownTimer = new Timer(500, e -> tick());
        snapshotTimer = new Timer(10_000, e -> sendSnapshot(""));
        monitorTimer = new Timer(4_000, e -> monitor());
        reconnectTimer = new Timer(5_000, e -> tryReconnect());
        installAntiCheat();
        countdownTimer.start();
        snapshotTimer.start();
        monitorTimer.start();
        reconnectTimer.start();
        clearClipboard();
        tick();
        sendSnapshot("START");
    }

    // ----------------------------------------------------------------- UI
    private void buildUi(String name, String batch, String roll) {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setContentPane(root);

        // top bar
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BLUE, 2),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        JLabel title = new JLabel(exam.getTitle() + "   |   " + name + "  Batch " + batch + "  Roll " + roll);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        timerLabel.setFont(new Font("Monospaced", Font.BOLD, 30));
        timerLabel.setPreferredSize(new Dimension(150, 40));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        warningLabel.setForeground(Theme.RED);
        right.add(warningLabel);
        right.add(statusLabel);
        right.add(timerLabel);
        submitButton.setFont(new Font("SansSerif", Font.BOLD, 15));
        submitButton.setBackground(Theme.GREEN);
        submitButton.setForeground(Color.WHITE);
        submitButton.setOpaque(true);
        right.add(submitButton);
        top.add(title, BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        // left: question viewer
        JTextArea question = new JTextArea(exam.getQuestionText());
        question.setEditable(false);
        question.setLineWrap(true);
        question.setWrapStyleWord(true);
        question.setFont(new Font("SansSerif", Font.PLAIN, 16));
        question.setMargin(new Insets(8, 8, 8, 8));
        question.setTransferHandler(new BlockingTransferHandler(null));
        JPanel qPanel = new JPanel(new BorderLayout());
        JPanel qContent = new JPanel(new BorderLayout());
        qContent.add(question, BorderLayout.NORTH);
        if (exam.getQuestionImage() != null) {
            JLabel img = new JLabel(new ImageIcon(exam.getQuestionImage()));
            img.setHorizontalAlignment(SwingConstants.CENTER);
            qContent.add(img, BorderLayout.CENTER);
        }
        qPanel.add(new JScrollPane(qContent), BorderLayout.CENTER);
        qPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY),
                "Question Paper  (" + exam.getTotalMarks() + " marks, " + exam.getDurationMinutes() + " min)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 14)));
        qPanel.setMinimumSize(new Dimension(300, 200));

        // right: editor + output
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        runButton.setBackground(Theme.GREEN);
        runButton.setForeground(Color.WHITE);
        runButton.setOpaque(true);
        runButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        bar.add(runButton);
        bar.add(new JLabel("Language:"));
        bar.add(languageBox);
        bannerLabel.setForeground(Theme.RED);
        bannerLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        bar.add(bannerLabel);

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.add(bar, BorderLayout.NORTH);
        editorPanel.add(editor.createScrollPane(), BorderLayout.CENTER);

        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        outputArea.setBackground(new Color(0x1E, 0x1E, 0x1E));
        outputArea.setForeground(new Color(0xE0, 0xE0, 0xE0));
        outputArea.setTransferHandler(new BlockingTransferHandler(null));
        inputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        inputArea.setTransferHandler(new BlockingTransferHandler(new BlockingTransferHandler.Reporter() {
            @Override public void pasteAttempt() { report(CheatEvent.Kind.PASTE_ATTEMPT, "Input box"); }
            @Override public void copyAttempt() { report(CheatEvent.Kind.COPY_ATTEMPT, "Input box"); }
        }));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Output", new JScrollPane(outputArea));
        tabs.addTab("Program Input (stdin)", new JScrollPane(inputArea));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, tabs);
        rightSplit.setResizeWeight(0.72);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, qPanel, rightSplit);
        mainSplit.setResizeWeight(0.38);
        if (exam.hasMcq()) {
            mcqPanel = new McqPanel(exam.getMcqQuestions());
            mcqPanel.setChangeListener(answers -> client.send(new Message(Message.Type.MCQ_ANSWERS, answers)));
        }
        if (mcqPanel != null && exam.hasCoding()) {
            JTabbedPane parts = new JTabbedPane();
            parts.addTab("Part A: MCQ", mcqPanel);
            parts.addTab("Part B: Written / Coding", mainSplit);
            root.add(parts, BorderLayout.CENTER);
        } else if (mcqPanel != null) {
            root.add(mcqPanel, BorderLayout.CENTER);
        } else {
            root.add(mainSplit, BorderLayout.CENTER);
        }

        // listeners
        runButton.addActionListener(e -> runCode());
        submitButton.addActionListener(e -> confirmSubmit());
        languageBox.addActionListener(e -> languageChanged());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                JOptionPane.showMessageDialog(ExamFrame.this,
                        "You cannot close the exam window. Use the Submit button when you are finished.");
            }
        });
    }

    private void installAntiCheat() {
        editor.setTransferHandler(new BlockingTransferHandler(new BlockingTransferHandler.Reporter() {
            @Override public void pasteAttempt() { report(CheatEvent.Kind.PASTE_ATTEMPT, "Editor"); }
            @Override public void copyAttempt() { report(CheatEvent.Kind.COPY_ATTEMPT, "Editor"); }
        }));
        editor.setLargeInsertListener(len ->
                report(CheatEvent.Kind.LARGE_INSERT, len + " characters inserted at once"));

        addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override public void windowLostFocus(WindowEvent e) {
                if (finished || submitting || isOwnWindow(e.getOppositeWindow())) return;
                focusLostAt = System.currentTimeMillis();
                report(CheatEvent.Kind.FOCUS_LOST, "Left the exam window");
            }
            @Override public void windowGainedFocus(WindowEvent e) {
                if (finished || focusLostAt == 0) return;
                long secs = (System.currentTimeMillis() - focusLostAt) / 1000;
                focusLostAt = 0;
                report(CheatEvent.Kind.FOCUS_RETURNED, "Away for " + secs + " seconds");
            }
        });
    }

    private boolean isOwnWindow(Window w) {
        while (w != null) {
            if (w == this) return true;
            w = w.getOwner();
        }
        return false;
    }

    // ---------------------------------------------------------- reporting
    /** Records an event locally (banner + counter) and sends it to the teacher. May be called from any thread. */
    private void report(CheatEvent.Kind kind, String detail) {
        final CheatEvent ev = new CheatEvent(kind, detail);
        client.send(new Message(Message.Type.CHEAT_EVENT, ev));
        SwingUtilities.invokeLater(() -> {
            if (ev.isWarning()) {
                warnings++;
                warningLabel.setText("Warnings: " + warnings);
                showBanner("\u26A0 " + kind.getLabel() + " - recorded and sent to the teacher");
            }
        });
    }

    private void showBanner(String text) {
        bannerLabel.setText(text);
        Timer t = new Timer(6000, e -> bannerLabel.setText(" "));
        t.setRepeats(false);
        t.start();
    }

    // ------------------------------------------------------------ monitor
    private void monitor() {
        if (finished || submitting) return;
        clearClipboard();
        final java.util.List<String> forbidden = exam.getForbiddenApps();
        final boolean checkDisplays = exam.isBlockMultiDisplay();
        Thread t = new Thread(() -> {
            if (checkDisplays) {
                int count = AntiCheat.displayCount();
                if (count > 1 && lastDisplayCount <= 1) {
                    report(CheatEvent.Kind.MULTI_DISPLAY, count + " displays connected");
                }
                lastDisplayCount = count;
            }
            Set<String> running = AntiCheat.findForbiddenProcesses(forbidden);
            for (String app : running) {
                if (reportedApps.add(app)) report(CheatEvent.Kind.FORBIDDEN_APP, app);
            }
            reportedApps.retainAll(running); // allow a new report if it is closed and opened again
        }, "exam-monitor");
        t.setDaemon(true);
        t.start();
    }

    private void clearClipboard() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(""), null);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------- timer
    private void tick() {
        long remaining = Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000_000L);
        timerLabel.setText(String.format("%02d:%02d", remaining / 60, remaining % 60));
        timerLabel.setForeground(remaining <= 300 ? Theme.RED : Color.BLACK);
        if (remaining <= 0 && !submitting && !finished) {
            doSubmit(true);
        }
    }

    // ---------------------------------------------------------- snapshots
    private void sendSnapshot(String note) {
        if (!exam.hasCoding()) {
            client.send(new Message(Message.Type.HEARTBEAT));
            return;
        }
        String code = editor.getCode();
        if (note.isEmpty() && code.equals(lastSentCode)) {
            client.send(new Message(Message.Type.HEARTBEAT));
            return;
        }
        lastSentCode = code;
        client.send(new Message(Message.Type.SNAPSHOT, (String) languageBox.getSelectedItem(),
                new Snapshot(System.currentTimeMillis(), code, note)));
    }

    // ---------------------------------------------------------- language
    private String templateFor(String lang) {
        if ("C++".equals(lang)) return CPP_TEMPLATE;
        if ("C".equals(lang)) return C_TEMPLATE;
        return JAVA_TEMPLATE;
    }

    private void languageChanged() {
        String lang = (String) languageBox.getSelectedItem();
        if (lang == null || lang.equals(previousLanguage)) return;
        if (editor.getCode().equals(templateFor(previousLanguage))) {
            editor.setCodeSilently(templateFor(lang));
        }
        previousLanguage = lang;
        editor.setLanguage(lang);
    }

    // ------------------------------------------------------------- run
    private void runCode() {
        final String code = editor.getCode();
        final String lang = (String) languageBox.getSelectedItem();
        final String stdin = inputArea.getText();
        sendSnapshot("RUN");
        runButton.setEnabled(false);
        outputArea.setText("Compiling and running...\n");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return CodeRunner.run(lang, code, stdin); }
            @Override protected void done() {
                try { outputArea.setText(get()); } catch (Exception ex) { outputArea.setText("Error: " + ex); }
                outputArea.setCaretPosition(0);
                runButton.setEnabled(!submitting && !finished);
            }
        }.execute();
    }

    // ----------------------------------------------------------- submit
    private void confirmSubmit() {
        int r = JOptionPane.showConfirmDialog(this,
                "Submit your answer now? You cannot edit it after submitting.",
                "Confirm Submit", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) doSubmit(false);
    }

    private void doSubmit(final boolean auto) {
        if (submitting || finished) return;
        submitting = true;
        editor.setEditable(false);
        if (mcqPanel != null) mcqPanel.setInputEnabled(false);
        runButton.setEnabled(false);
        submitButton.setEnabled(false);
        languageBox.setEnabled(false);
        submitButton.setText("Submitting...");

        final String code = exam.hasCoding() ? editor.getCode() : "";
        final String lang = (String) languageBox.getSelectedItem();
        final int[] mcq = mcqPanel == null ? null : mcqPanel.getAnswers();
        Thread t = new Thread(() -> {
            boolean ok = false;
            for (int attempt = 0; attempt < 5 && !ok; attempt++) {
                if (!client.isConnected()) {
                    try { client.reconnect(); } catch (Exception ignored) { }
                }
                if (client.isConnected()) {
                    Submission s = new Submission();
                    s.setFinalCode(code);
                    s.setLanguage(lang);
                    s.setMcqAnswers(mcq);
                    s.setAutoSubmitted(auto);
                    ackLatch = new CountDownLatch(1);
                    client.send(new Message(Message.Type.SUBMIT, s));
                    try { ok = ackLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                } else {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
                }
            }
            final boolean success = ok;
            SwingUtilities.invokeLater(() -> finishSubmit(success, code, lang, auto));
        }, "exam-submit");
        t.setDaemon(true);
        t.start();
    }

    private void finishSubmit(boolean success, String code, String lang, boolean auto) {
        if (success) {
            finished = true;
            countdownTimer.stop();
            snapshotTimer.stop();
            monitorTimer.stop();
            reconnectTimer.stop();
            setAlwaysOnTop(false);
            JOptionPane.showMessageDialog(this,
                    (auto ? "Time is over. Your answer was submitted automatically."
                          : "Your answer was submitted successfully.") + "\nYou may leave now. Thank you!",
                    "Submitted", JOptionPane.INFORMATION_MESSAGE);
            client.close();
            dispose();
            System.exit(0);
        } else {
            String path = saveBackup(code, lang);
            submitting = false;
            submitButton.setEnabled(true);
            if (mcqPanel != null) mcqPanel.setInputEnabled(true);
            submitButton.setText("Retry Submit");
            JOptionPane.showMessageDialog(this,
                    "Could not reach the server, so your answer is NOT submitted yet.\n"
                    + "A backup copy was saved to:\n" + path
                    + "\nCall the invigilator, then press \"Retry Submit\".",
                    "Submission failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String saveBackup(String code, String lang) {
        try {
            File f = new File(System.getProperty("user.home"),
                    "SmartExam_backup_" + student.getRoll().replaceAll("[^A-Za-z0-9]", "_") + ".txt");
            try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
                pw.print(code);
            }
            return f.getAbsolutePath();
        } catch (Exception ex) {
            return "(backup failed: " + ex.getMessage() + ")";
        }
    }

    // ------------------------------------------------------ reconnecting
    private void tryReconnect() {
        if (finished || submitting || client.isConnected() || reconnecting) return;
        reconnecting = true;
        Thread t = new Thread(() -> {
            try {
                ExamPacket p = client.reconnect();
                deadlineNanos = System.nanoTime() + p.getRemainingSeconds() * 1_000_000_000L;
                SwingUtilities.invokeLater(() -> setConnectedStatus(true));
            } catch (Exception ignored) {
                // will try again in a few seconds
            } finally {
                reconnecting = false;
            }
        }, "exam-reconnect");
        t.setDaemon(true);
        t.start();
    }

    private void setConnectedStatus(boolean ok) {
        statusLabel.setText(ok ? "Connected" : "DISCONNECTED - retrying...");
        statusLabel.setForeground(ok ? Theme.GREEN : Theme.RED);
    }

    // -------------------------------------- ExamClient.Listener callbacks
    @Override
    public void onForceSubmit(String reason) {
        SwingUtilities.invokeLater(() -> {
            showBanner("Submitting automatically" + (reason == null ? "" : " (" + reason + ")"));
            doSubmit(true);
        });
    }

    @Override
    public void onBroadcast(String text) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, text, "Message from teacher", JOptionPane.INFORMATION_MESSAGE));
    }

    @Override
    public void onSubmitAck() {
        CountDownLatch l = ackLatch;
        if (l != null) l.countDown();
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        SwingUtilities.invokeLater(() -> setConnectedStatus(connected));
    }
}
