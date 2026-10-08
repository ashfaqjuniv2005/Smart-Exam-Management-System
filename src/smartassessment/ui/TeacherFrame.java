package smartassessment.ui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import smartassessment.model.AssessmentResult;
import smartassessment.model.Exam;
import smartassessment.model.Question;
import smartassessment.model.StudentSession;
import smartassessment.model.Submission;
import smartassessment.model.Submission.CheatEvent;
import smartassessment.model.Submission.Snapshot;
import smartassessment.network.ExamServer;
import smartassessment.network.Protocol;
import smartassessment.service.ExamRepository;
import smartassessment.service.ExamService;
import smartassessment.util.Theme;

/**
 * Main window of the teacher with three tabs: exam setup (including the question manager),
 * live monitor (start server & exam / end / restart the exam) and submissions with marking and replay.
 */
public class TeacherFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final ExamService service = new ExamService(new ExamRepository());

    public TeacherFrame() {
        super("Smart Assessment - Teacher Panel");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        final ExamSetupPanel setup = new ExamSetupPanel(service);
        final MonitorPanel monitor = new MonitorPanel(setup, service);
        final SubmissionsPanel submissions = new SubmissionsPanel(service);

        final JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("1. Exam Setup", setup);
        tabs.addTab("2. Live Monitor", monitor);
        tabs.addTab("3. Submissions & Marks", submissions);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == submissions) submissions.reload();
        });
        add(tabs);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                int r = JOptionPane.showConfirmDialog(TeacherFrame.this,
                        "Close the teacher panel? The server will stop.", "Exit", JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) {
                    monitor.shutdown();
                    dispose();
                    System.exit(0);
                }
            }
        });
        setSize(1150, 760);
        setLocationRelativeTo(null);
    }

    /** Text area listing cheat events (used by the monitor and the submissions tab). */
    static JTextArea eventText(List<CheatEvent> events) {
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        for (CheatEvent e : events) {
            sb.append(fmt.format(new Date(e.getTimeMillis()))).append("  ")
              .append(e.isWarning() ? "[WARNING] " : "[info]    ")
              .append(e.getKind().getLabel());
            if (!e.getDetail().isEmpty()) sb.append(" - ").append(e.getDetail());
            sb.append('\n');
        }
        if (events.isEmpty()) sb.append("No events recorded.");
        JTextArea area = new JTextArea(sb.toString(), 16, 60);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        return area;
    }

    // ===================================================================== TAB 1
    /** Create, save and load exams. Questions are managed in the Question Manager window. */
    private static class ExamSetupPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final ExamService service;
        private final JTextField titleField = new JTextField(24);
        private final JTextField codeField = new JTextField(10);
        private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(35, 1, 600, 1));
        private final JSpinner warningsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        private final JCheckBox multiDisplayBox = new JCheckBox("Block multiple displays", true);
        private final JTextField forbiddenField = new JTextField(20);
        private final JTextArea rollsArea = new JTextArea(6, 14);
        private final JComboBox<String> savedBox = new JComboBox<>();
        private final DefaultListModel<Question> previewModel = new DefaultListModel<>();
        private final JLabel summaryLabel = new JLabel();
        private final List<Question> questions = new ArrayList<>();
        private QuestionManagerFrame manager;

        ExamSetupPanel(ExamService service) {
            this.service = service;
            setLayout(new BorderLayout(8, 8));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // ---- settings
            JPanel settings = new JPanel(new GridBagLayout());
            settings.setBorder(BorderFactory.createTitledBorder("Exam settings"));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 6, 4, 6);
            c.anchor = GridBagConstraints.WEST;
            addRow(settings, c, 0, "Exam title:", titleField, "Exam code (students type this):", codeField);
            addRow(settings, c, 1, "Duration (minutes):", durationSpinner, "Max warnings (0 = unlimited):", warningsSpinner);
            addRow(settings, c, 2, "", new JLabel(""), "", multiDisplayBox);
            c.gridx = 0; c.gridy = 3; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            settings.add(new JLabel("Forbidden apps (comma separated):"), c);
            c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            forbiddenField.setText(String.join(", ", Exam.DEFAULT_FORBIDDEN_APPS));
            settings.add(forbiddenField, c);

            rollsArea.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY),
                    "Allowed exam rolls (one per line, blank = everyone)",
                    javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP));
            JPanel north = new JPanel(new BorderLayout(8, 0));
            north.add(settings, BorderLayout.CENTER);
            north.add(new JScrollPane(rollsArea), BorderLayout.EAST);
            add(north, BorderLayout.NORTH);

            // ---- questions overview
            JPanel qPanel = new JPanel(new BorderLayout(6, 6));
            qPanel.setBorder(BorderFactory.createTitledBorder("Questions"));
            JButton manageBtn = new JButton("Manage Questions  (add, generate, import txt / csv / pdf)");
            manageBtn.setFont(manageBtn.getFont().deriveFont(Font.BOLD, 14f));
            JPanel top = new JPanel(new BorderLayout(10, 0));
            top.add(manageBtn, BorderLayout.WEST);
            top.add(summaryLabel, BorderLayout.CENTER);
            qPanel.add(top, BorderLayout.NORTH);
            qPanel.add(new JScrollPane(new JList<>(previewModel)), BorderLayout.CENTER);
            add(qPanel, BorderLayout.CENTER);

            // ---- buttons
            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            JButton newBtn = new JButton("New");
            JButton saveBtn = new JButton("Save exam");
            JButton loadBtn = new JButton("Load selected");
            bottom.add(newBtn);
            bottom.add(saveBtn);
            bottom.add(new JLabel("   Saved exams:"));
            bottom.add(savedBox);
            bottom.add(loadBtn);
            add(bottom, BorderLayout.SOUTH);

            manageBtn.addActionListener(e -> openManager());
            newBtn.addActionListener(e -> clearForm());
            saveBtn.addActionListener(e -> {
                Exam ex = buildExam(true);
                if (ex != null) {
                    try {
                        service.saveExam(ex);
                        refreshSaved();
                        JOptionPane.showMessageDialog(this, "Exam saved: " + ex.getCode());
                    } catch (Exception ex2) {
                        JOptionPane.showMessageDialog(this, "Could not save: " + ex2.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            loadBtn.addActionListener(e -> loadSelected());
            refreshSaved();
            updateSummary();
        }

        private void addRow(JPanel p, GridBagConstraints c, int row, String l1, JComponent f1, String l2, JComponent f2) {
            c.gridy = row;
            c.gridwidth = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.gridx = 0; p.add(new JLabel(l1), c);
            c.gridx = 1; c.weightx = 1;
            c.fill = f1 instanceof JTextField ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            p.add(f1, c);
            c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE; p.add(new JLabel(l2), c);
            c.gridx = 3; c.weightx = 1;
            c.fill = f2 instanceof JTextField ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            p.add(f2, c);
        }

        private void openManager() {
            if (manager != null && manager.isDisplayable()) {
                manager.toFront();
                return;
            }
            manager = new QuestionManagerFrame(service, questions, this::updateSummary);
            manager.setVisible(true);
        }

        private void closeManager() {
            if (manager != null) manager.dispose();
            manager = null;
        }

        /** Short description of the exam that is currently in the form (shown in the Live Monitor tab). */
        String currentExamLabel() {
            String code = codeField.getText().trim(), title = titleField.getText().trim();
            if (code.isEmpty() && title.isEmpty()) return "(nothing entered yet)";
            return code + " - " + title + "  [" + questions.size() + " questions]";
        }

        /** Refreshes the question list and the totals shown in this tab. */
        private void updateSummary() {
            previewModel.clear();
            int mcq = 0, written = 0, coding = 0, marks = 0;
            for (Question q : questions) {
                previewModel.addElement(q);
                if (q.isMcq()) mcq++;
                else if (q.isWritten()) written++;
                else coding++;
                marks += q.getMarks();
            }
            summaryLabel.setText("MCQ: " + mcq + "     Written: " + written + "     Coding: " + coding
                    + "     Total marks: " + marks);
        }

        void refreshSaved() {
            savedBox.removeAllItems();
            for (String code : service.listExamCodes()) savedBox.addItem(code);
        }

        private void loadSelected() {
            String code = (String) savedBox.getSelectedItem();
            if (code == null) return;
            try {
                Exam e = service.loadExam(code);
                closeManager();
                titleField.setText(e.getTitle());
                codeField.setText(e.getCode());
                durationSpinner.setValue(e.getDurationMinutes());
                warningsSpinner.setValue(e.getMaxWarnings());
                multiDisplayBox.setSelected(e.isBlockMultiDisplay());
                forbiddenField.setText(String.join(", ", e.getForbiddenApps()));
                rollsArea.setText(String.join("\n", e.getAllowedRolls()));
                questions.clear();
                questions.addAll(e.getQuestions());
                updateSummary();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not load: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void clearForm() {
            closeManager();
            titleField.setText("");
            codeField.setText("");
            durationSpinner.setValue(35);
            warningsSpinner.setValue(0);
            multiDisplayBox.setSelected(true);
            forbiddenField.setText(String.join(", ", Exam.DEFAULT_FORBIDDEN_APPS));
            rollsArea.setText("");
            questions.clear();
            updateSummary();
        }

        /** Builds an Exam from the form; returns null (and optionally shows why) if it is incomplete. */
        Exam buildExam(boolean showErrors) {
            Exam e = new Exam();
            e.setTitle(titleField.getText().trim());
            e.setCode(codeField.getText().trim());
            e.setDurationMinutes((Integer) durationSpinner.getValue());
            e.setMaxWarnings((Integer) warningsSpinner.getValue());
            e.setBlockMultiDisplay(multiDisplayBox.isSelected());
            e.setQuestions(new ArrayList<>(questions));
            List<String> apps = new ArrayList<>();
            for (String s : forbiddenField.getText().split(",")) {
                if (!s.trim().isEmpty()) apps.add(s.trim());
            }
            e.setForbiddenApps(apps);
            List<String> rolls = new ArrayList<>();
            for (String s : rollsArea.getText().split("\\R")) {
                if (!s.trim().isEmpty()) rolls.add(s.trim());
            }
            e.setAllowedRolls(rolls);

            String problem = service.validateExam(e);
            if (problem != null) {
                if (showErrors) JOptionPane.showMessageDialog(this, problem, "Missing information",
                        JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return e;
        }
    }

    // ===================================================================== TAB 2
    /** Run the LAN server, start / end / restart the exam and watch students live. */
    private static class MonitorPanel extends JPanel implements ExamServer.Listener {
        private static final long serialVersionUID = 1L;

        private final ExamSetupPanel setupPanel;
        private final ExamService service;
        private final JButton startServerBtn = new JButton("1. Start Server & Exam");
        private final JButton endExamBtn = new JButton("2. End Exam");
        private final JButton restartBtn = new JButton("3. Restart this Exam (for students who missed it)");
        private final JLabel addressLabel = new JLabel("Server not running");
        private final JLabel hintLabel = new JLabel(" ");
        private final JLabel timeLabel = new JLabel("Time left: --:--");
        private final JTextField messageField = new JTextField(28);
        private final JTextArea logArea = new JTextArea();
        private final SessionTableModel model = new SessionTableModel();
        private final JTable table = new JTable(model);
        private ExamServer server;

        MonitorPanel(ExamSetupPanel setupPanel, ExamService service) {
            this.setupPanel = setupPanel;
            this.service = service;
            setLayout(new BorderLayout(8, 8));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel controls = new JPanel(new GridLayout(3, 1, 4, 4));
            JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            row1.add(startServerBtn);
            row1.add(endExamBtn);
            row1.add(restartBtn);
            timeLabel.setFont(new Font("Monospaced", Font.BOLD, 20));
            row1.add(Box.createHorizontalStrut(14));
            row1.add(timeLabel);
            JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            row2.add(addressLabel);
            row2.add(Box.createHorizontalStrut(20));
            row2.add(new JLabel("Message to all students:"));
            row2.add(messageField);
            JButton sendBtn = new JButton("Send");
            JButton eventsBtn = new JButton("View selected student's events");
            row2.add(sendBtn);
            row2.add(eventsBtn);
            hintLabel.setForeground(new Color(0x1B, 0x5E, 0x20));
            JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            row3.add(hintLabel);
            controls.add(row1);
            controls.add(row2);
            controls.add(row3);
            add(controls, BorderLayout.NORTH);

            table.setRowHeight(24);
            table.setDefaultRenderer(Object.class, new RowRenderer());
            JScrollPane tableScroll = new JScrollPane(table);
            tableScroll.setBorder(BorderFactory.createTitledBorder("Students"));
            logArea.setEditable(false);
            logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            JScrollPane logScroll = new JScrollPane(logArea);
            logScroll.setBorder(BorderFactory.createTitledBorder("Live log"));
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, logScroll);
            split.setResizeWeight(0.6);
            add(split, BorderLayout.CENTER);

            startServerBtn.addActionListener(e -> startServer());
            endExamBtn.addActionListener(e -> endExam());
            restartBtn.addActionListener(e -> restartExam());
            sendBtn.addActionListener(e -> sendMessage());
            eventsBtn.addActionListener(e -> showEvents());
            updateButtons();

            new Timer(1000, e -> {
                updateButtons();
                if (server != null) {
                    if (!server.isExamStarted()) {
                        timeLabel.setText("Waiting to start");
                    } else if (server.isExamEnded()) {
                        timeLabel.setText("EXAM ENDED");
                    } else {
                        long s = server.getRemainingSeconds();
                        timeLabel.setText(String.format("Time left: %02d:%02d", s / 60, s % 60));
                    }
                    model.fireTableDataChanged();
                }
            }).start();
        }

        /** Buttons follow the state of the server and the exam. */
        private void updateButtons() {
            boolean running = server != null;
            // a new server (for example for another subject) can be started once the exam has ended
            startServerBtn.setEnabled(!running || server.isExamEnded());
            startServerBtn.setText(running && server.isExamEnded() ? "1. Start Another Exam" : "1. Start Server & Exam");
            String next = setupPanel.currentExamLabel();
            if (!running) {
                hintLabel.setText("Exam to start: " + next + "   (choose or create it in the Exam Setup tab)");
            } else if (server.isExamEnded()) {
                hintLabel.setText("Exam ended. To run ANOTHER subject: open Exam Setup, load / create that exam, come back and press "
                        + "\"Start Another Exam\".   Next exam: " + next);
            } else {
                hintLabel.setText("Running: " + server.getExam().getCode() + " - " + server.getExam().getTitle()
                        + "   (press End Exam when finished)");
            }
            endExamBtn.setEnabled(running && server.isExamStarted() && !server.isExamEnded());
            restartBtn.setEnabled(running && server.isExamEnded());
        }

        private void startServer() {
            Exam exam = setupPanel.buildExam(true);
            if (exam == null) return;
            int r = JOptionPane.showConfirmDialog(this, "Start the server and begin the exam \"" + exam.getTitle()
                    + "\" (code " + exam.getCode() + ") now?\nThe timer starts immediately for " + exam.getDurationMinutes()
                    + " minutes.", "Start Server & Exam", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
            try {
                if (server != null) { // the previous exam has ended: close its server first
                    server.stop();
                    server = null;
                    logArea.append("\n------------------- new exam -------------------\n");
                }
                service.saveExam(exam);
                ExamServer next = new ExamServer(exam, Protocol.DEFAULT_PORT, service, this);
                next.start();
                next.startExam();
                server = next;
                addressLabel.setText("Students connect to: " + localAddresses() + "   (port "
                        + Protocol.DEFAULT_PORT + ", code: " + exam.getCode() + ")");
                model.setServer(server);
                model.fireTableDataChanged();
                updateButtons();
            } catch (Exception ex) {
                server = null;
                updateButtons();
                JOptionPane.showMessageDialog(this, "Could not start server: " + ex.getMessage()
                        + "\n(Is another server already running on port " + Protocol.DEFAULT_PORT + "?)",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void endExam() {
            if (server == null) return;
            int r = JOptionPane.showConfirmDialog(this, "End the exam and force all students to submit?",
                    "End Exam", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) {
                server.endExam();
                updateButtons();
            }
        }

        /** Re-opens the ended exam, with the same questions, for students who did not submit. */
        private void restartExam() {
            if (server == null) return;
            JTextField minutes = new JTextField(String.valueOf(server.getExam().getDurationMinutes()), 6);
            JTextField rolls = new JTextField(30);
            JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
            p.add(new JLabel("Duration of this extra sitting (minutes):"));
            p.add(minutes);
            p.add(new JLabel("Only these exam rolls may join (comma separated, blank = every student who has not submitted):"));
            p.add(rolls);
            p.add(new JLabel("<html><i>The questions stay exactly the same. Students who already submitted stay locked out.</i></html>"));
            if (JOptionPane.showConfirmDialog(this, p, "Restart Exam", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            int mins;
            try {
                mins = Integer.parseInt(minutes.getText().trim());
                if (mins < 1 || mins > 600) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter the duration as a number of minutes (1-600).");
                return;
            }
            List<String> list = new ArrayList<>();
            for (String s : rolls.getText().split(",")) {
                if (!s.trim().isEmpty()) list.add(s.trim());
            }
            server.restartExam(mins, list);
            updateButtons();
        }

        private void sendMessage() {
            String text = messageField.getText().trim();
            if (server != null && !text.isEmpty()) {
                server.broadcast(text);
                messageField.setText("");
            }
        }

        private void showEvents() {
            int row = table.getSelectedRow();
            if (row < 0 || server == null) {
                JOptionPane.showMessageDialog(this, "Select a student in the table first.");
                return;
            }
            StudentSession s = model.get(table.convertRowIndexToModel(row));
            JOptionPane.showMessageDialog(this, new JScrollPane(eventText(s.getEventsCopy())),
                    "Events - " + s.getStudent(), JOptionPane.PLAIN_MESSAGE);
        }

        private static String localAddresses() {
            List<String> ips = new ArrayList<>();
            try {
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                        if (a instanceof Inet4Address) ips.add(a.getHostAddress());
                    }
                }
            } catch (Exception ignored) { }
            return ips.isEmpty() ? "127.0.0.1" : String.join(" / ", ips);
        }

        void shutdown() {
            if (server != null) server.stop();
        }

        // ---------------------------------------------- ExamServer.Listener
        @Override
        public void onSessionsChanged() {
            SwingUtilities.invokeLater(model::fireTableDataChanged);
        }

        @Override
        public void onLog(String message) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }

        // ------------------------------------------------------ table model
        private static class SessionTableModel extends AbstractTableModel {
            private static final long serialVersionUID = 1L;
            private final String[] cols = {"Roll", "Name", "Batch", "Status", "Warnings", "Last snapshot", "Submitted"};
            private ExamServer server;
            private List<StudentSession> rows = new ArrayList<>();

            void setServer(ExamServer s) { this.server = s; }

            StudentSession get(int i) { return rows.get(i); }

            @Override public void fireTableDataChanged() {
                rows = server == null ? new ArrayList<StudentSession>() : server.getSessions();
                super.fireTableDataChanged();
            }

            @Override public int getRowCount() { return rows.size(); }
            @Override public int getColumnCount() { return cols.length; }
            @Override public String getColumnName(int c) { return cols[c]; }

            @Override public Object getValueAt(int r, int c) {
                if (r >= rows.size()) return "";
                StudentSession s = rows.get(r);
                switch (c) {
                    case 0: return s.getStudent().getRoll();
                    case 1: return s.getStudent().getName();
                    case 2: return s.getStudent().getBatch();
                    case 3: return s.isSubmitted() ? "Submitted" : (s.isConnected() ? "Online" : "OFFLINE");
                    case 4: return s.getWarnings();
                    case 5: return s.getLastSnapshotTime() == 0 ? "-"
                            : new SimpleDateFormat("HH:mm:ss").format(new Date(s.getLastSnapshotTime()));
                    default: return s.isSubmitted() ? "Yes" : "No";
                }
            }
        }

        /** Green = submitted, red = many warnings, orange = some warnings / offline. */
        private class RowRenderer extends DefaultTableCellRenderer {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                if (!sel) {
                    StudentSession s = model.get(t.convertRowIndexToModel(r));
                    Color bg = Color.WHITE;
                    if (s.isSubmitted()) bg = new Color(0xD7, 0xF5, 0xD7);
                    else if (s.getWarnings() >= 3) bg = new Color(0xFF, 0xCD, 0xCD);
                    else if (s.getWarnings() > 0 || !s.isConnected()) bg = new Color(0xFF, 0xEB, 0xC8);
                    comp.setBackground(bg);
                    comp.setForeground(Color.BLACK);
                }
                return comp;
            }
        }
    }

    // ===================================================================== TAB 3
    /** Browse saved submissions, mark the written part, view code, replay, check evidence, export. */
    private static class SubmissionsPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final ExamService service;
        private final JComboBox<String> examBox = new JComboBox<>();
        private final SubmissionModel model = new SubmissionModel();
        private final JTable table = new JTable(model);
        private Exam currentExam;

        SubmissionsPanel(ExamService service) {
            this.service = service;
            setLayout(new BorderLayout(8, 8));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            JButton refresh = new JButton("Refresh");
            JButton exportBtn = new JButton("Export CSV...");
            top.add(new JLabel("Exam:"));
            top.add(examBox);
            top.add(refresh);
            top.add(exportBtn);
            add(top, BorderLayout.NORTH);

            table.setRowHeight(24);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            add(new JScrollPane(table), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            JButton gradeBtn = new JButton("Give marks (written / coding)");
            JButton writtenBtn = new JButton("View written answers");
            JButton viewBtn = new JButton("View coding answers");
            JButton mcqBtn = new JButton("MCQ answer sheet");
            JButton replayBtn = new JButton("Replay how the code was written");
            JButton reportBtn = new JButton("Anti-cheat report");
            bottom.add(gradeBtn);
            bottom.add(writtenBtn);
            bottom.add(viewBtn);
            bottom.add(mcqBtn);
            bottom.add(replayBtn);
            bottom.add(reportBtn);
            add(bottom, BorderLayout.SOUTH);

            refresh.addActionListener(e -> reload());
            examBox.addActionListener(e -> loadSubmissions());
            exportBtn.addActionListener(e -> export());
            gradeBtn.addActionListener(e -> grade());
            writtenBtn.addActionListener(e -> viewWritten());
            viewBtn.addActionListener(e -> viewCode());
            mcqBtn.addActionListener(e -> mcqSheet());
            replayBtn.addActionListener(e -> replay());
            reportBtn.addActionListener(e -> report());
            reload();
        }

        /** Reloads exam list and table. */
        void reload() {
            Object selected = examBox.getSelectedItem();
            examBox.removeAllItems();
            for (String c : service.listExamCodes()) examBox.addItem(c);
            if (selected != null) examBox.setSelectedItem(selected);
            loadSubmissions();
        }

        private void loadSubmissions() {
            String code = (String) examBox.getSelectedItem();
            currentExam = null;
            List<Submission> subs = new ArrayList<>();
            if (code != null) {
                try {
                    currentExam = service.loadExam(code);
                    subs = service.loadSubmissions(code);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Could not load exam " + code + ": " + ex.getMessage());
                }
            }
            model.setData(service, currentExam, subs);
        }

        private Submission selected() {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Select a submission first.");
                return null;
            }
            return model.get(table.convertRowIndexToModel(row));
        }

        private void grade() {
            Submission s = selected();
            if (s == null || currentExam == null) return;
            boolean w = currentExam.hasWritten(), c = currentExam.hasCoding();
            if (!w && !c) {
                JOptionPane.showMessageDialog(this, "This exam has no written or coding questions.");
                return;
            }
            AssessmentResult r = service.evaluate(currentExam, s);
            JTextField wf = new JTextField(r.isWrittenGraded() ? String.valueOf(r.getWrittenMarks()) : "", 6);
            JTextField cf = new JTextField(r.isCodingGraded() ? String.valueOf(r.getCodingMarks()) : "", 6);
            JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
            if (w) {
                p.add(new JLabel("Written marks (0 - " + currentExam.getWrittenTotalMarks() + "):"));
                p.add(wf);
            }
            if (c) {
                p.add(new JLabel("Coding marks (0 - " + currentExam.getCodingTotalMarks() + "):"));
                p.add(cf);
            }
            if (JOptionPane.showConfirmDialog(this, p, "Marks for " + s.getStudent(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            try {
                int row = table.getSelectedRow();
                if (w && !wf.getText().trim().isEmpty()) {
                    service.setWrittenMarks(currentExam, s, parseMarks(wf.getText(), currentExam.getWrittenTotalMarks()));
                }
                if (c && !cf.getText().trim().isEmpty()) {
                    service.setCodingMarks(currentExam, s, parseMarks(cf.getText(), currentExam.getCodingTotalMarks()));
                }
                loadSubmissions();
                if (row >= 0 && row < model.getRowCount()) table.setRowSelectionInterval(row, row);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter whole numbers within the allowed range.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not save marks: " + ex.getMessage());
            }
        }

        private static int parseMarks(String text, int total) {
            int v = Integer.parseInt(text.trim());
            if (v < 0 || v > total) throw new NumberFormatException();
            return v;
        }

        private void viewWritten() {
            Submission s = selected();
            if (s == null || currentExam == null) return;
            if (!currentExam.hasWritten()) {
                JOptionPane.showMessageDialog(this, "This exam has no written questions.");
                return;
            }
            JTextArea area = new JTextArea(service.buildWrittenSheet(currentExam, s), 26, 80);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            area.setCaretPosition(0);
            JOptionPane.showMessageDialog(this, new JScrollPane(area),
                    s.getStudent() + " - written answers", JOptionPane.PLAIN_MESSAGE);
        }

        private void viewCode() {
            Submission s = selected();
            if (s == null || currentExam == null) return;
            List<Question> cq = currentExam.getCodingQuestions();
            if (cq.isEmpty()) {
                JOptionPane.showMessageDialog(this, "This exam has no coding questions.");
                return;
            }
            JTabbedPane tabs = new JTabbedPane();
            for (int i = 0; i < cq.size(); i++) {
                String lang = i < s.getLanguages().size() ? s.getLanguages().get(i) : "Java";
                String code = i < s.getCodeAnswers().size() ? s.getCodeAnswers().get(i) : "";
                SyntaxEditor pane = new SyntaxEditor();
                pane.setLanguage(lang);
                pane.setCodeSilently(code);
                pane.setEditable(false);
                JTextArea q = new JTextArea(cq.get(i).getText(), 3, 40);
                q.setEditable(false);
                q.setLineWrap(true);
                q.setWrapStyleWord(true);
                q.setBackground(new Color(0xF5, 0xF5, 0xDC));
                JPanel p = new JPanel(new BorderLayout(4, 4));
                p.add(new JScrollPane(q), BorderLayout.NORTH);
                p.add(pane.createScrollPane(), BorderLayout.CENTER);
                tabs.addTab("Q" + (i + 1) + " (" + cq.get(i).getMarks() + " marks)", p);
            }
            tabs.setPreferredSize(new Dimension(820, 560));
            JOptionPane.showMessageDialog(this, tabs, s.getStudent() + " - coding answers", JOptionPane.PLAIN_MESSAGE);
        }

        private void mcqSheet() {
            Submission s = selected();
            if (s == null || currentExam == null) return;
            if (!currentExam.hasMcq()) {
                JOptionPane.showMessageDialog(this, "This exam has no MCQ part.");
                return;
            }
            JTextArea area = new JTextArea(service.buildMcqSheet(currentExam, s), 24, 70);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            area.setCaretPosition(0);
            JOptionPane.showMessageDialog(this, new JScrollPane(area),
                    "MCQ answer sheet - " + s.getStudent(), JOptionPane.PLAIN_MESSAGE);
        }

        private void replay() {
            Submission s = selected();
            if (s == null) return;
            if (s.getSnapshots().isEmpty()) {
                JOptionPane.showMessageDialog(this, "No code snapshots were recorded for this student.");
                return;
            }
            new ReplayDialog(SwingUtilities.getWindowAncestor(this), s).setVisible(true);
        }

        private void report() {
            Submission s = selected();
            if (s == null) return;
            JOptionPane.showMessageDialog(this, new JScrollPane(eventText(s.getEvents())),
                    "Anti-cheat report - " + s.getStudent() + "  (warnings: " + s.getWarnings() + ")",
                    JOptionPane.PLAIN_MESSAGE);
        }

        private void export() {
            if (currentExam == null) return;
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File(currentExam.getCode() + "_results.csv"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    service.exportCsv(currentExam, model.data, fc.getSelectedFile());
                    JOptionPane.showMessageDialog(this, "Exported to " + fc.getSelectedFile());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private static class SubmissionModel extends AbstractTableModel {
            private static final long serialVersionUID = 1L;
            private final String[] cols = {"Roll", "Name", "Batch", "Submitted at", "Warnings", "Type",
                "MCQ", "Written", "Coding", "Total"};
            private List<Submission> data = new ArrayList<>();
            private List<AssessmentResult> results = new ArrayList<>();

            void setData(ExamService service, Exam exam, List<Submission> d) {
                data = d;
                results = new ArrayList<>();
                for (Submission s : d) results.add(exam == null ? null : service.evaluate(exam, s));
                fireTableDataChanged();
            }

            Submission get(int i) { return data.get(i); }

            @Override public int getRowCount() { return data.size(); }
            @Override public int getColumnCount() { return cols.length; }
            @Override public String getColumnName(int c) { return cols[c]; }

            @Override public Object getValueAt(int r, int c) {
                Submission s = data.get(r);
                AssessmentResult a = results.get(r);
                switch (c) {
                    case 0: return s.getStudent().getRoll();
                    case 1: return s.getStudent().getName();
                    case 2: return s.getStudent().getBatch();
                    case 3: return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(s.getSubmittedAt()));
                    case 4: return s.getWarnings();
                    case 5:
                        if (s.isCollectedByServer()) return "Collected by server";
                        return s.isAutoSubmitted() ? "Auto-submitted" : "Submitted by student";
                    case 6: return a == null || a.getMcqTotal() == 0 ? "-" : a.getMcqScore() + " / " + a.getMcqTotal();
                    case 7: return a == null || a.getWrittenTotal() == 0 ? "-"
                            : (a.isWrittenGraded() ? a.getWrittenMarks() + " / " + a.getWrittenTotal() : "not graded");
                    case 8: return a == null || a.getCodingTotal() == 0 ? "-"
                            : (a.isCodingGraded() ? a.getCodingMarks() + " / " + a.getCodingTotal() : "not graded");
                    default: return a == null ? "-" : a.getTotalScore() + " / " + a.getTotalMarks();
                }
            }
        }
    }

    // ===================================================================== replay
    /** Lets the teacher scrub through the saved snapshots to see how the code was developed. */
    private static class ReplayDialog extends JDialog {
        private static final long serialVersionUID = 1L;

        private final List<Snapshot> snapshots;
        private final SyntaxEditor viewer = new SyntaxEditor();
        private final JSlider slider;
        private final JLabel infoLabel = new JLabel(" ");
        private final Timer playTimer;
        private final JButton playBtn = new JButton("\u25B6 Play");

        ReplayDialog(Window owner, Submission sub) {
            super(owner, "Replay - " + sub.getStudent(), ModalityType.APPLICATION_MODAL);
            this.snapshots = sub.getSnapshots();
            setSize(1000, 680);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(6, 6));

            viewer.setEditable(false);
            add(viewer.createScrollPane(), BorderLayout.CENTER);

            DefaultListModel<String> lm = new DefaultListModel<>();
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
            final List<CheatEvent> events = sub.getEvents();
            for (CheatEvent e : events) {
                lm.addElement(fmt.format(new Date(e.getTimeMillis())) + "  " + (e.isWarning() ? "\u26A0 " : "")
                        + e.getKind().getLabel() + (e.getDetail().isEmpty() ? "" : " - " + e.getDetail()));
            }
            final JList<String> eventList = new JList<>(lm);
            JScrollPane evScroll = new JScrollPane(eventList);
            evScroll.setPreferredSize(new Dimension(300, 100));
            evScroll.setBorder(BorderFactory.createTitledBorder("Events (click to jump to that moment)"));
            add(evScroll, BorderLayout.EAST);

            int max = Math.max(0, snapshots.size() - 1);
            slider = new JSlider(0, max, max);
            slider.setPaintTicks(max <= 60);
            slider.setMajorTickSpacing(Math.max(1, max / 10));
            JButton prev = new JButton("\u25C0 Prev");
            JButton next = new JButton("Next \u25B6");
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            buttons.add(prev);
            buttons.add(next);
            buttons.add(playBtn);
            buttons.add(infoLabel);
            JPanel south = new JPanel(new BorderLayout());
            south.add(slider, BorderLayout.NORTH);
            south.add(buttons, BorderLayout.CENTER);
            add(south, BorderLayout.SOUTH);

            slider.addChangeListener(e -> show(slider.getValue()));
            prev.addActionListener(e -> slider.setValue(slider.getValue() - 1));
            next.addActionListener(e -> slider.setValue(slider.getValue() + 1));
            playTimer = new Timer(700, e -> {
                if (slider.getValue() >= slider.getMaximum()) stopPlay();
                else slider.setValue(slider.getValue() + 1);
            });
            playBtn.addActionListener(e -> {
                if (playTimer.isRunning()) {
                    stopPlay();
                } else {
                    if (slider.getValue() >= slider.getMaximum()) slider.setValue(0);
                    playTimer.start();
                    playBtn.setText("\u23F8 Pause");
                }
            });
            eventList.addListSelectionListener(e -> {
                int i = eventList.getSelectedIndex();
                if (!e.getValueIsAdjusting() && i >= 0) jumpToTime(events.get(i).getTimeMillis());
            });
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) { playTimer.stop(); }
            });
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            show(max);
        }

        private void stopPlay() {
            playTimer.stop();
            playBtn.setText("\u25B6 Play");
        }

        private void jumpToTime(long time) {
            int best = 0;
            for (int i = 0; i < snapshots.size(); i++) {
                if (snapshots.get(i).getTimeMillis() <= time) best = i;
            }
            slider.setValue(best);
        }

        private void show(int index) {
            if (snapshots.isEmpty()) {
                infoLabel.setText("No snapshots were recorded.");
                return;
            }
            index = Math.max(0, Math.min(index, snapshots.size() - 1));
            Snapshot s = snapshots.get(index);
            viewer.setLanguage(s.getLanguage());
            viewer.setCodeSilently(s.getCode());
            long start = snapshots.get(0).getTimeMillis();
            long secs = (s.getTimeMillis() - start) / 1000;
            int previous = -1;
            for (int i = index - 1; i >= 0; i--) {
                if (snapshots.get(i).getQuestionNo() == s.getQuestionNo()) { previous = i; break; }
            }
            int delta = previous < 0 ? s.getCode().length()
                    : s.getCode().length() - snapshots.get(previous).getCode().length();
            String note = s.getNote().isEmpty() ? "" : "   [" + s.getNote() + "]";
            infoLabel.setText(String.format("   Q%d   |   Snapshot %d/%d   |   +%02d:%02d after start   |   %d characters (%+d)%s",
                    s.getQuestionNo() + 1, index + 1, snapshots.size(), secs / 60, secs % 60,
                    s.getCode().length(), delta, note));
            infoLabel.setForeground(Math.abs(delta) > 150 && previous >= 0 ? Theme.RED : Color.BLACK);
        }
    }
}
