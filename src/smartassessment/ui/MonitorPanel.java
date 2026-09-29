package smartassessment.ui;

import java.awt.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import smartassessment.model.CheatEvent;
import smartassessment.model.Exam;
import smartassessment.server.ExamServer;
import smartassessment.server.StudentSession;
import smartassessment.util.FileStore;

/** Teacher tab: run the LAN server, start/stop the exam and watch students live. */
public class MonitorPanel extends JPanel implements ExamServer.Listener {
    private static final long serialVersionUID = 1L;

    private final ExamSetupPanel setupPanel;
    private final JButton startServerBtn = new JButton("1. Start Server");
    private final JButton startExamBtn = new JButton("2. Start Exam");
    private final JButton endExamBtn = new JButton("3. End Exam");
    private final JLabel addressLabel = new JLabel("Server not running");
    private final JLabel timeLabel = new JLabel("Time left: --:--");
    private final JTextField messageField = new JTextField(28);
    private final JTextArea logArea = new JTextArea();
    private final SessionTableModel model = new SessionTableModel();
    private final JTable table = new JTable(model);
    private ExamServer server;

    public MonitorPanel(ExamSetupPanel setupPanel) {
        this.setupPanel = setupPanel;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new GridLayout(2, 1, 4, 4));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row1.add(startServerBtn);
        row1.add(startExamBtn);
        row1.add(endExamBtn);
        timeLabel.setFont(new Font("Monospaced", Font.BOLD, 20));
        row1.add(Box.createHorizontalStrut(20));
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
        controls.add(row1);
        controls.add(row2);
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

        startExamBtn.setEnabled(false);
        endExamBtn.setEnabled(false);
        startServerBtn.addActionListener(e -> startServer());
        startExamBtn.addActionListener(e -> startExam());
        endExamBtn.addActionListener(e -> endExam());
        sendBtn.addActionListener(e -> sendMessage());
        eventsBtn.addActionListener(e -> showEvents());

        new Timer(1000, e -> {
            if (server != null) {
                long s = server.getRemainingSeconds();
                timeLabel.setText(String.format("Time left: %02d:%02d", s / 60, s % 60));
                model.fireTableDataChanged();
            }
        }).start();
    }

    private void startServer() {
        Exam exam = setupPanel.buildExam(true);
        if (exam == null) return;
        try {
            FileStore.saveExam(exam);
            server = new ExamServer(exam, 5555, this);
            server.start();
            startServerBtn.setEnabled(false);
            startExamBtn.setEnabled(true);
            addressLabel.setText("Students connect to: " + localAddresses() + "   (port 5555, code: " + exam.getCode() + ")");
            model.setServer(server);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not start server: " + ex.getMessage()
                    + "\n(Is another server already running on port 5555?)", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startExam() {
        if (server == null) return;
        int r = JOptionPane.showConfirmDialog(this, "Start the exam now? The timer begins for everyone.",
                "Start Exam", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        server.startExam();
        startExamBtn.setEnabled(false);
        endExamBtn.setEnabled(true);
    }

    private void endExam() {
        if (server == null) return;
        int r = JOptionPane.showConfirmDialog(this, "End the exam and force all students to submit?",
                "End Exam", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        server.endExam();
        endExamBtn.setEnabled(false);
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
                "Events - " + s.getInfo(), JOptionPane.PLAIN_MESSAGE);
    }

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

    public void shutdown() {
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

    // -------------------------------------------------------- table model
    private static class SessionTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] cols = {"Roll", "Name", "Batch", "Status", "Warnings", "Last snapshot", "Submitted"};
        private ExamServer server;
        private List<StudentSession> rows = new ArrayList<>();

        void setServer(ExamServer s) { this.server = s; }

        StudentSession get(int i) { return rows.get(i); }

        @Override public void fireTableDataChanged() {
            rows = server == null ? new ArrayList<>() : server.getSessions();
            super.fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override public Object getValueAt(int r, int c) {
            if (r >= rows.size()) return "";
            StudentSession s = rows.get(r);
            switch (c) {
                case 0: return s.getInfo().getRoll();
                case 1: return s.getInfo().getName();
                case 2: return s.getInfo().getBatch();
                case 3: return s.isSubmitted() ? "Submitted" : (s.isConnected() ? "Online" : "OFFLINE");
                case 4: return s.getWarnings();
                case 5: return s.getLastSnapshotTime() == 0 ? "-"
                        : new SimpleDateFormat("HH:mm:ss").format(new Date(s.getLastSnapshotTime()));
                default: return s.isSubmitted() ? "Yes" : "No";
            }
        }
    }

    /** Colours rows: green = submitted, red = many warnings, orange = some warnings / offline. */
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
