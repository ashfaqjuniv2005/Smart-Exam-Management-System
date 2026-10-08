package smartassessment.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import smartassessment.model.Exam;
import smartassessment.model.Question;
import smartassessment.model.Student;
import smartassessment.model.Submission;
import smartassessment.model.Submission.CheatEvent;
import smartassessment.model.Submission.Snapshot;
import smartassessment.network.ExamClient;
import smartassessment.network.Message;
import smartassessment.network.Protocol;
import smartassessment.service.CodeRunner;
import smartassessment.util.Theme;

/**
 * The student's exam portal: MCQ part + written / coding part (question viewer, code editor),
 * timer and anti-cheat monitoring.
 */
public class ExamFrame extends JFrame implements ExamClient.Listener {
    private static final long serialVersionUID = 1L;

    private static final String JAVA_TEMPLATE =
            "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n"
            + "        \n    }\n}\n";
    private static final String CPP_TEMPLATE =
            "#include <iostream>\nusing namespace std;\n\nint main() {\n    \n    return 0;\n}\n";
    private static final String C_TEMPLATE =
            "#include <stdio.h>\n\nint main() {\n    \n    return 0;\n}\n";

    // =================================================== nested helper classes
    /** Blocks copy, cut, paste and drag & drop, and reports each attempt. */
    static class BlockingTransferHandler extends TransferHandler {
        private static final long serialVersionUID = 1L;

        /** Receives the blocked attempts. */
        interface Reporter {
            void pasteAttempt();
            void copyAttempt();
        }

        private final transient Reporter reporter;

        BlockingTransferHandler(Reporter reporter) { this.reporter = reporter; }

        @Override
        public int getSourceActions(JComponent c) { return COPY_OR_MOVE; }

        @Override
        public void exportToClipboard(JComponent comp, Clipboard clip, int action) {
            if (reporter != null) reporter.copyAttempt();
            // nothing is copied
        }

        @Override
        public boolean canImport(TransferSupport support) { return false; }

        @Override
        public boolean importData(TransferSupport support) {
            if (reporter != null) reporter.pasteAttempt();
            return false;
        }
    }

    /** Student side: shows all MCQ questions with radio buttons. */
    static class McqPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        /** Called every time the student changes an answer. */
        interface ChangeListener {
            void answersChanged(int[] answers);
        }

        /** Panel that follows the width of the scroll pane, so long text wraps. */
        private static class WidthTrackingPanel extends JPanel implements Scrollable {
            private static final long serialVersionUID = 1L;
            WidthTrackingPanel() { super(new GridBagLayout()); }
            @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
            @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
            @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 200; }
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
            @Override public boolean getScrollableTracksViewportHeight() { return false; }
        }

        private final int[] answers;
        private final List<List<JRadioButton>> radios = new ArrayList<>();
        private final List<JButton> clearButtons = new ArrayList<>();
        private final JLabel progressLabel = new JLabel();
        private final JScrollPane scroll;
        private transient ChangeListener listener;
        private boolean updating;

        McqPanel(List<Question> questions) {
            setLayout(new BorderLayout(6, 6));
            answers = new int[questions.size()];
            Arrays.fill(answers, -1);

            JPanel list = new WidthTrackingPanel();
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(6, 8, 6, 8);
            for (int i = 0; i < questions.size(); i++) {
                list.add(buildCard(i, questions.get(i)), c);
            }
            c.weighty = 1;
            c.fill = GridBagConstraints.BOTH;
            list.add(Box.createGlue(), c);

            progressLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
            top.add(new JLabel("Choose the correct answer for each question."));
            top.add(progressLabel);
            add(top, BorderLayout.NORTH);
            scroll = new JScrollPane(list);
            scroll.getVerticalScrollBar().setUnitIncrement(24);
            add(scroll, BorderLayout.CENTER);
            updateProgress();
        }

        private JPanel buildCard(final int index, Question q) {
            JPanel card = new JPanel(new BorderLayout(4, 4));
            card.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Theme.BLUE, 1),
                    "Question " + (index + 1) + "   (" + q.getMarks() + (q.getMarks() == 1 ? " mark)" : " marks)"),
                    TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 14)));

            JTextArea text = new JTextArea(q.getText());
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setOpaque(false);
            text.setFont(new Font("SansSerif", Font.PLAIN, 16));
            text.setTransferHandler(new BlockingTransferHandler(null));
            card.add(text, BorderLayout.NORTH);

            JPanel options = new JPanel();
            options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
            options.setOpaque(false);
            final ButtonGroup group = new ButtonGroup();
            List<JRadioButton> row = new ArrayList<>();
            for (int j = 0; j < q.getOptions().size(); j++) {
                final int opt = j;
                JRadioButton rb = new JRadioButton((char) ('A' + j) + ".  " + q.getOptions().get(j));
                rb.setFont(new Font("SansSerif", Font.PLAIN, 15));
                rb.setOpaque(false);
                rb.setAlignmentX(Component.LEFT_ALIGNMENT);
                rb.addActionListener(e -> select(index, opt));
                group.add(rb);
                options.add(rb);
                row.add(rb);
            }
            radios.add(row);
            card.add(options, BorderLayout.CENTER);

            JButton clear = new JButton("Clear answer");
            clear.addActionListener(e -> {
                group.clearSelection();
                select(index, -1);
            });
            clearButtons.add(clear);
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            south.setOpaque(false);
            south.add(clear);
            card.add(south, BorderLayout.SOUTH);
            return card;
        }

        private void select(int question, int option) {
            if (updating) return;
            answers[question] = option;
            updateProgress();
            if (listener != null) listener.answersChanged(answers.clone());
        }

        private void updateProgress() {
            int done = 0;
            for (int a : answers) if (a >= 0) done++;
            progressLabel.setText("Answered: " + done + " / " + answers.length);
        }

        void setChangeListener(ChangeListener l) { this.listener = l; }

        /** Shows the first question (the scroll pane may jump to the end while it is laid out). */
        void scrollToTop() {
            scroll.getVerticalScrollBar().setValue(0);
        }

        int[] getAnswers() { return answers.clone(); }

        /** Restores answers after a reconnect. */
        void setAnswers(int[] saved) {
            if (saved == null || saved.length != answers.length) return;
            updating = true;
            for (int i = 0; i < saved.length; i++) {
                answers[i] = saved[i];
                if (saved[i] >= 0 && saved[i] < radios.get(i).size()) radios.get(i).get(saved[i]).setSelected(true);
            }
            updating = false;
            updateProgress();
        }

        /** Locks or unlocks all answer buttons (used after Submit). */
        void setInputEnabled(boolean enabled) {
            for (List<JRadioButton> row : radios) for (JRadioButton rb : row) rb.setEnabled(enabled);
            for (JButton b : clearButtons) b.setEnabled(enabled);
        }
    }

    /** Student side: shows the written questions, each with a text box for the typed answer. */
    static class WrittenPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final List<JTextArea> answers = new ArrayList<>();
        private final JLabel progressLabel = new JLabel();
        private final JScrollPane scroll;
        private boolean updating;
        private volatile boolean dirty;

        WrittenPanel(List<Question> questions, BlockingTransferHandler.Reporter reporter,
                     java.util.function.IntConsumer largeInsert) {
            setLayout(new BorderLayout(6, 6));
            JPanel list = new McqPanel.WidthTrackingPanel();
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(6, 8, 6, 8);
            for (int i = 0; i < questions.size(); i++) {
                list.add(buildCard(i, questions.get(i), reporter, largeInsert), c);
            }
            c.weighty = 1;
            c.fill = GridBagConstraints.BOTH;
            list.add(Box.createGlue(), c);

            progressLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
            top.add(new JLabel("Type your answer in the box under each question."));
            top.add(progressLabel);
            add(top, BorderLayout.NORTH);
            scroll = new JScrollPane(list);
            scroll.getVerticalScrollBar().setUnitIncrement(24);
            add(scroll, BorderLayout.CENTER);
            updateProgress();
        }

        private JPanel buildCard(int index, Question q, BlockingTransferHandler.Reporter reporter,
                                 java.util.function.IntConsumer largeInsert) {
            JPanel card = new JPanel(new BorderLayout(4, 4));
            card.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Theme.BLUE, 1),
                    "Question " + (index + 1) + "   (" + q.getMarks() + (q.getMarks() == 1 ? " mark)" : " marks)"),
                    TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 14)));

            JTextArea text = new JTextArea(q.getText());
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setOpaque(false);
            text.setFont(new Font("SansSerif", Font.PLAIN, 16));
            text.setTransferHandler(new BlockingTransferHandler(null));
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(text, BorderLayout.NORTH);
            if (q.getImage() != null) {
                JLabel img = new JLabel(new ImageIcon(q.getImage()));
                img.setHorizontalAlignment(SwingConstants.CENTER);
                head.add(img, BorderLayout.CENTER);
            }
            card.add(head, BorderLayout.NORTH);

            final JTextArea answer = new JTextArea(7, 40);
            answer.setLineWrap(true);
            answer.setWrapStyleWord(true);
            answer.setFont(new Font("SansSerif", Font.PLAIN, 15));
            answer.setMargin(new Insets(4, 6, 4, 6));
            answer.setTransferHandler(new BlockingTransferHandler(reporter));
            answer.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    if (updating) return;
                    dirty = true;
                    updateProgress();
                    if (e.getLength() > 30 && largeInsert != null) largeInsert.accept(e.getLength());
                }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    if (updating) return;
                    dirty = true;
                    updateProgress();
                }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
            });
            answers.add(answer);
            JScrollPane sp = new JScrollPane(answer);
            sp.setBorder(BorderFactory.createTitledBorder("Your answer"));
            sp.setPreferredSize(new Dimension(100, 190));
            card.add(sp, BorderLayout.CENTER);
            return card;
        }

        private void updateProgress() {
            int done = 0;
            for (JTextArea a : answers) if (!a.getText().trim().isEmpty()) done++;
            progressLabel.setText("Answered: " + done + " / " + answers.size());
        }

        String[] getAnswers() {
            String[] r = new String[answers.size()];
            for (int i = 0; i < r.length; i++) r[i] = answers.get(i).getText();
            return r;
        }

        /** Restores answers after a reconnect. */
        void setAnswers(String[] saved) {
            if (saved == null) return;
            updating = true;
            for (int i = 0; i < saved.length && i < answers.size(); i++) {
                if (saved[i] != null) answers.get(i).setText(saved[i]);
            }
            updating = false;
            updateProgress();
        }

        /** True once after the text changed, so the caller sends it to the teacher. */
        boolean consumeDirty() {
            boolean d = dirty;
            dirty = false;
            return d;
        }

        void setInputEnabled(boolean enabled) {
            for (JTextArea a : answers) a.setEditable(enabled);
        }

        void scrollToTop() {
            scroll.getVerticalScrollBar().setValue(0);
        }
    }

    // ============================================================== fields
    private final ExamClient client;
    private final Exam exam;
    private final Student student;
    private final List<Question> codingQuestions;
    private final String[] codes;     // student's code per written/coding question (null = not opened yet)
    private final String[] langs;
    private final String[] lastSent;

    private final SyntaxEditor editor = new SyntaxEditor();
    private final JTextArea outputArea = new JTextArea();
    private final JTextArea inputArea = new JTextArea();
    private final JComboBox<String> languageBox = new JComboBox<>(new String[]{"Java", "C++", "C"});
    private final JButton runButton = new JButton("\u25B6 Run");
    private final JButton submitButton = new JButton("Submit");
    private final JLabel timerLabel = new JLabel("00:00", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Connected");
    private final JLabel warningLabel = new JLabel("Warnings: 0");
    private final JLabel bannerLabel = new JLabel(" ");
    private final JTextArea questionText = new JTextArea();
    private final JLabel imageLabel = new JLabel();
    private final JPanel questionPanel = new JPanel(new BorderLayout(4, 4));
    private JComboBox<String> questionBox;
    private McqPanel mcqPanel;         // null when the exam has no MCQ part
    private WrittenPanel writtenPanel; // null when the exam has no written part

    private final Timer countdownTimer;
    private final Timer snapshotTimer;
    private final Timer monitorTimer;
    private final Timer reconnectTimer;

    private long deadlineNanos;
    private int current;            // index of the written/coding question shown in the editor
    private boolean switching;
    private String previousLanguage = "Java";
    private long focusLostAt;
    private int warnings;
    private int lastDisplayCount = 1;
    private boolean reconnecting;
    private volatile boolean submitting;
    private volatile boolean finished;
    private volatile CountDownLatch ackLatch;
    private final Set<String> reportedApps = new TreeSet<>();

    public ExamFrame(ExamClient client, Protocol.ExamPacket packet) {
        super("Smart Assessment - Exam Portal");
        this.client = client;
        this.exam = packet.getExam();
        this.student = client.getStudent();
        this.codingQuestions = exam.getCodingQuestions();
        int n = codingQuestions.size();
        codes = new String[n];
        langs = new String[n];
        lastSent = new String[n];
        String[] rc = packet.getRestoredCodes();
        String[] rl = packet.getRestoredLanguages();
        for (int i = 0; i < n; i++) {
            langs[i] = rl != null && i < rl.length && rl[i] != null ? rl[i] : "Java";
            codes[i] = rc != null && i < rc.length && rc[i] != null && !rc[i].isEmpty() ? rc[i] : null;
            lastSent[i] = codes[i] == null ? "" : codes[i];
        }
        client.setListener(this);

        setUndecorated(true);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        buildUi();
        if (mcqPanel != null) mcqPanel.setAnswers(packet.getRestoredMcqAnswers());
        if (writtenPanel != null) writtenPanel.setAnswers(packet.getRestoredWrittenAnswers());
        if (n > 0) showCodingQuestion(0);

        deadlineNanos = System.nanoTime() + packet.getRemainingSeconds() * 1_000_000_000L;
        countdownTimer = new Timer(500, e -> tick());
        snapshotTimer = new Timer(Protocol.SNAPSHOT_INTERVAL_MS, e -> {
            sendSnapshot("");
            sendWrittenIfChanged();
        });
        monitorTimer = new Timer(Protocol.MONITOR_INTERVAL_MS, e -> monitor());
        reconnectTimer = new Timer(Protocol.RECONNECT_INTERVAL_MS, e -> tryReconnect());
        installAntiCheat();
        countdownTimer.start();
        snapshotTimer.start();
        monitorTimer.start();
        reconnectTimer.start();
        clearClipboard();
        tick();
        sendSnapshot("START");
        if (mcqPanel != null) SwingUtilities.invokeLater(() -> mcqPanel.scrollToTop());
        if (writtenPanel != null) SwingUtilities.invokeLater(() -> writtenPanel.scrollToTop());
    }

    // ===================================================================== UI
    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setContentPane(root);

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BLUE, 2),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        JLabel title = new JLabel(exam.getTitle() + "   |   " + student.getName() + "  Batch " + student.getBatch()
                + "  Roll " + student.getRoll() + "   |   " + exam.getTotalMarks() + " marks");
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

        JSplitPane codingPart = codingQuestions.isEmpty() ? null : buildCodingPart();
        if (exam.hasMcq()) {
            mcqPanel = new McqPanel(exam.getMcqQuestions());
            mcqPanel.setChangeListener(answers -> client.send(new Message(Protocol.Type.MCQ_ANSWERS, answers)));
        }
        if (exam.hasWritten()) {
            writtenPanel = new WrittenPanel(exam.getWrittenQuestions(),
                    new BlockingTransferHandler.Reporter() {
                        @Override public void pasteAttempt() { report(CheatEvent.Kind.PASTE_ATTEMPT, "Written answer"); }
                        @Override public void copyAttempt() { report(CheatEvent.Kind.COPY_ATTEMPT, "Written answer"); }
                    },
                    len -> report(CheatEvent.Kind.LARGE_INSERT, len + " characters inserted at once"));
        }
        List<String> titles = new ArrayList<>();
        List<Component> parts = new ArrayList<>();
        if (mcqPanel != null) { titles.add("MCQ"); parts.add(mcqPanel); }
        if (writtenPanel != null) { titles.add("Written"); parts.add(writtenPanel); }
        if (codingPart != null) { titles.add("Coding"); parts.add(codingPart); }
        if (parts.size() == 1) {
            root.add(parts.get(0), BorderLayout.CENTER);
        } else if (parts.size() > 1) {
            JTabbedPane tabs = new JTabbedPane();
            tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 14f));
            for (int i = 0; i < parts.size(); i++) tabs.addTab(titles.get(i), parts.get(i));
            root.add(tabs, BorderLayout.CENTER);
        }

        submitButton.addActionListener(e -> confirmSubmit());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                JOptionPane.showMessageDialog(ExamFrame.this,
                        "You cannot close the exam window. Use the Submit button when you are finished.");
            }
        });
    }

    private JSplitPane buildCodingPart() {
        // ---- left: question viewer
        questionText.setEditable(false);
        questionText.setLineWrap(true);
        questionText.setWrapStyleWord(true);
        questionText.setFont(new Font("SansSerif", Font.PLAIN, 16));
        questionText.setMargin(new Insets(8, 8, 8, 8));
        questionText.setTransferHandler(new BlockingTransferHandler(null));
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel content = new JPanel(new BorderLayout());
        content.add(questionText, BorderLayout.NORTH);
        content.add(imageLabel, BorderLayout.CENTER);
        if (codingQuestions.size() > 1) {
            String[] names = new String[codingQuestions.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = "Question " + (i + 1) + "  (" + codingQuestions.get(i).getMarks() + " marks)";
            }
            questionBox = new JComboBox<>(names);
            questionBox.addActionListener(e -> {
                if (!switching) switchQuestion(questionBox.getSelectedIndex());
            });
            questionPanel.add(questionBox, BorderLayout.NORTH);
        }
        questionPanel.add(new JScrollPane(content), BorderLayout.CENTER);
        questionPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY),
                "Question", TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 14)));
        questionPanel.setMinimumSize(new Dimension(300, 200));

        // ---- right: editor + output
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
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, questionPanel, rightSplit);
        mainSplit.setResizeWeight(0.38);

        runButton.addActionListener(e -> runCode());
        languageBox.addActionListener(e -> languageChanged());
        return mainSplit;
    }

    private void installAntiCheat() {
        editor.setTransferHandler(new BlockingTransferHandler(new BlockingTransferHandler.Reporter() {
            @Override public void pasteAttempt() { report(CheatEvent.Kind.PASTE_ATTEMPT, "Editor"); }
            @Override public void copyAttempt() { report(CheatEvent.Kind.COPY_ATTEMPT, "Editor"); }
        }));
        editor.setLargeInsertListener(len ->
                report(CheatEvent.Kind.LARGE_INSERT, len + " characters inserted at once"));

        addWindowFocusListener(new WindowFocusListener() {
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

    // ========================================================== questions
    /** Shows written/coding question number idx and loads that question's code into the editor. */
    private void showCodingQuestion(int idx) {
        switching = true;
        Question q = codingQuestions.get(idx);
        questionText.setText(q.getText());
        questionText.setCaretPosition(0);
        imageLabel.setIcon(q.getImage() == null ? null : new ImageIcon(q.getImage()));
        ((TitledBorder) questionPanel.getBorder()).setTitle("Question " + (idx + 1) + " of "
                + codingQuestions.size() + "   (" + q.getMarks() + " marks)");
        questionPanel.repaint();
        String lang = langs[idx];
        languageBox.setSelectedItem(lang);
        previousLanguage = lang;
        editor.setLanguage(lang);
        editor.setCodeSilently(codes[idx] != null ? codes[idx] : templateFor(lang));
        switching = false;
    }

    /** Stores what is in the editor into the answer of the current question. */
    private void commitCurrent() {
        if (codingQuestions.isEmpty()) return;
        codes[current] = editor.getCode();
        langs[current] = (String) languageBox.getSelectedItem();
    }

    private void switchQuestion(int newIndex) {
        if (newIndex < 0 || newIndex == current) return;
        commitCurrent();
        sendSnapshot("SWITCH"); // records the code of the question we are leaving
        current = newIndex;
        showCodingQuestion(current);
    }

    // ============================================================ reporting
    /** Records an event locally (banner + counter) and sends it to the teacher. May be called from any thread. */
    private void report(CheatEvent.Kind kind, String detail) {
        final CheatEvent ev = new CheatEvent(kind, detail);
        client.send(new Message(Protocol.Type.CHEAT_EVENT, ev));
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

    // ============================================================== monitor
    private void monitor() {
        if (finished || submitting) return;
        clearClipboard();
        final List<String> forbidden = exam.getForbiddenApps();
        final boolean checkDisplays = exam.isBlockMultiDisplay();
        Thread t = new Thread(() -> {
            if (checkDisplays) {
                int count = displayCount();
                if (count > 1 && lastDisplayCount <= 1) {
                    report(CheatEvent.Kind.MULTI_DISPLAY, count + " displays connected");
                }
                lastDisplayCount = count;
            }
            Set<String> running = findForbiddenProcesses(forbidden);
            for (String app : running) {
                if (reportedApps.add(app)) report(CheatEvent.Kind.FORBIDDEN_APP, app);
            }
            reportedApps.retainAll(running); // report again if it is closed and opened again
        }, "exam-monitor");
        t.setDaemon(true);
        t.start();
    }

    /** Number of monitors connected to this computer. */
    public static int displayCount() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
        } catch (Throwable t) {
            return 1;
        }
    }

    /** Names of running programs that appear in the forbidden list. */
    static Set<String> findForbiddenProcesses(List<String> forbidden) {
        final Set<String> found = new TreeSet<>();
        if (forbidden == null || forbidden.isEmpty()) return found;
        final Set<String> wanted = new TreeSet<>();
        for (String f : forbidden) {
            String n = normalise(f);
            if (!n.isEmpty()) wanted.add(n);
        }
        try {
            ProcessHandle.allProcesses().forEach(ph -> {
                String cmd = ph.info().command().orElse("");
                if (cmd.isEmpty()) return;
                String name = normalise(new File(cmd).getName());
                if (wanted.contains(name)) found.add(name);
            });
        } catch (Throwable ignored) {
            // some systems do not allow listing processes; monitoring is then skipped
        }
        return found;
    }

    private static String normalise(String s) {
        String n = s == null ? "" : s.trim().toLowerCase();
        if (n.endsWith(".exe")) n = n.substring(0, n.length() - 4);
        return n;
    }

    private void clearClipboard() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(""), null);
        } catch (Exception ignored) { }
    }

    // ================================================================ timer
    private void tick() {
        long remaining = Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000_000L);
        timerLabel.setText(String.format("%02d:%02d", remaining / 60, remaining % 60));
        timerLabel.setForeground(remaining <= 300 ? Theme.RED : Color.BLACK);
        if (remaining <= 0 && !submitting && !finished) {
            doSubmit(true);
        }
    }

    private void sendWrittenIfChanged() {
        if (writtenPanel != null && writtenPanel.consumeDirty()) {
            client.send(new Message(Protocol.Type.WRITTEN_ANSWERS, writtenPanel.getAnswers()));
        }
    }

    // ============================================================ snapshots
    private void sendSnapshot(String note) {
        if (codingQuestions.isEmpty()) {
            client.send(new Message(Protocol.Type.HEARTBEAT));
            return;
        }
        String code = editor.getCode();
        if (note.isEmpty() && code.equals(lastSent[current])) {
            client.send(new Message(Protocol.Type.HEARTBEAT));
            return;
        }
        lastSent[current] = code;
        client.send(new Message(Protocol.Type.SNAPSHOT, new Snapshot(System.currentTimeMillis(), current,
                (String) languageBox.getSelectedItem(), code, note)));
    }

    // ============================================================ language
    private String templateFor(String lang) {
        if ("C++".equals(lang)) return CPP_TEMPLATE;
        if ("C".equals(lang)) return C_TEMPLATE;
        return JAVA_TEMPLATE;
    }

    private void languageChanged() {
        if (switching) return;
        String lang = (String) languageBox.getSelectedItem();
        if (lang == null || lang.equals(previousLanguage)) return;
        if (editor.getCode().equals(templateFor(previousLanguage))) {
            editor.setCodeSilently(templateFor(lang));
        }
        previousLanguage = lang;
        langs[current] = lang;
        editor.setLanguage(lang);
    }

    // ================================================================== run
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

    // ============================================================== submit
    private void confirmSubmit() {
        int r = JOptionPane.showConfirmDialog(this,
                "Submit your answers now? You cannot change them after submitting.",
                "Confirm Submit", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) doSubmit(false);
    }

    private void doSubmit(final boolean auto) {
        if (submitting || finished) return;
        submitting = true;
        commitCurrent();
        editor.setEditable(false);
        if (mcqPanel != null) mcqPanel.setInputEnabled(false);
        if (writtenPanel != null) writtenPanel.setInputEnabled(false);
        runButton.setEnabled(false);
        submitButton.setEnabled(false);
        languageBox.setEnabled(false);
        submitButton.setText("Submitting...");

        final List<String> codeList = new ArrayList<>();
        final List<String> langList = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            codeList.add(codes[i] == null ? "" : codes[i]);
            langList.add(langs[i]);
        }
        final int[] mcq = mcqPanel == null ? null : mcqPanel.getAnswers();
        final List<String> written = writtenPanel == null ? null
                : new ArrayList<>(Arrays.asList(writtenPanel.getAnswers()));

        Thread t = new Thread(() -> {
            boolean ok = false;
            for (int attempt = 0; attempt < 5 && !ok; attempt++) {
                if (!client.isConnected()) {
                    try { client.reconnect(); } catch (Exception ignored) { }
                }
                if (client.isConnected()) {
                    Submission s = new Submission();
                    s.setCodeAnswers(codeList);
                    s.setLanguages(langList);
                    s.setMcqAnswers(mcq);
                    if (written != null) s.setWrittenAnswers(written);
                    s.setAutoSubmitted(auto);
                    ackLatch = new CountDownLatch(1);
                    client.send(new Message(Protocol.Type.SUBMIT, s));
                    try { ok = ackLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                } else {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
                }
            }
            final boolean success = ok;
            SwingUtilities.invokeLater(() -> finishSubmit(success, codeList, auto));
        }, "exam-submit");
        t.setDaemon(true);
        t.start();
    }

    private void finishSubmit(boolean success, List<String> codeList, boolean auto) {
        if (success) {
            finished = true;
            countdownTimer.stop();
            snapshotTimer.stop();
            monitorTimer.stop();
            reconnectTimer.stop();
            setAlwaysOnTop(false);
            JOptionPane.showMessageDialog(this,
                    (auto ? "Time is over. Your answers were submitted automatically."
                          : "Your answers were submitted successfully.") + "\nYou may leave now. Thank you!",
                    "Submitted", JOptionPane.INFORMATION_MESSAGE);
            client.close();
            dispose();
            System.exit(0);
        } else {
            String path = saveBackup(codeList);
            submitting = false;
            submitButton.setEnabled(true);
            if (mcqPanel != null) mcqPanel.setInputEnabled(true);
            if (writtenPanel != null) writtenPanel.setInputEnabled(true);
            submitButton.setText("Retry Submit");
            JOptionPane.showMessageDialog(this,
                    "Could not reach the server, so your answers are NOT submitted yet.\n"
                    + "A backup copy of your code was saved to:\n" + path
                    + "\nCall the invigilator, then press \"Retry Submit\".",
                    "Submission failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String saveBackup(List<String> codeList) {
        try {
            File f = new File(System.getProperty("user.home"),
                    "SmartExam_backup_" + student.getRoll().replaceAll("[^A-Za-z0-9]", "_") + ".txt");
            try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
                for (int i = 0; i < codeList.size(); i++) {
                    pw.println("===== Question " + (i + 1) + " =====");
                    pw.println(codeList.get(i));
                }
                if (writtenPanel != null) {
                    String[] w = writtenPanel.getAnswers();
                    for (int i = 0; i < w.length; i++) {
                        pw.println("===== Written answer " + (i + 1) + " =====");
                        pw.println(w[i]);
                    }
                }
            }
            return f.getAbsolutePath();
        } catch (Exception ex) {
            return "(backup failed: " + ex.getMessage() + ")";
        }
    }

    // ======================================================== reconnecting
    private void tryReconnect() {
        if (finished || submitting || client.isConnected() || reconnecting) return;
        reconnecting = true;
        Thread t = new Thread(() -> {
            try {
                Protocol.ExamPacket p = client.reconnect();
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

    // ------------------------------------- ExamClient.Listener callbacks
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
