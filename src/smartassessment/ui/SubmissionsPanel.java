package smartassessment.ui;

import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import smartassessment.model.Submission;
import smartassessment.util.FileStore;

/** Teacher tab: browse saved submissions, view code, replay development, check evidence. */
public class SubmissionsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JComboBox<String> examBox = new JComboBox<>();
    private final SubmissionModel model = new SubmissionModel();
    private final JTable table = new JTable(model);

    public SubmissionsPanel() {
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
        JButton viewBtn = new JButton("View final code");
        JButton replayBtn = new JButton("Replay how the code was written");
        JButton reportBtn = new JButton("Anti-cheat report");
        JButton mcqBtn = new JButton("MCQ answer sheet");
        bottom.add(viewBtn);
        bottom.add(replayBtn);
        bottom.add(mcqBtn);
        bottom.add(reportBtn);
        add(bottom, BorderLayout.SOUTH);

        refresh.addActionListener(e -> reload());
        examBox.addActionListener(e -> loadSubmissions());
        exportBtn.addActionListener(e -> export());
        viewBtn.addActionListener(e -> viewCode());
        replayBtn.addActionListener(e -> replay());
        reportBtn.addActionListener(e -> report());
        mcqBtn.addActionListener(e -> mcqSheet());
        reload();
    }

    /** Reloads exam list and table. */
    public void reload() {
        Object selected = examBox.getSelectedItem();
        examBox.removeAllItems();
        for (String c : FileStore.listExamCodes()) examBox.addItem(c);
        if (selected != null) examBox.setSelectedItem(selected);
        loadSubmissions();
    }

    private void loadSubmissions() {
        String code = (String) examBox.getSelectedItem();
        model.setData(code == null ? new ArrayList<>() : FileStore.loadSubmissions(code));
    }

    private Submission selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a submission first.");
            return null;
        }
        return model.get(table.convertRowIndexToModel(row));
    }

    private void viewCode() {
        Submission s = selected();
        if (s == null) return;
        CodeEditorPane pane = new CodeEditorPane();
        pane.setLanguage(s.getLanguage());
        pane.setCodeSilently(s.getFinalCode());
        pane.setEditable(false);
        JScrollPane sp = pane.createScrollPane();
        sp.setPreferredSize(new Dimension(760, 520));
        JOptionPane.showMessageDialog(this, sp, s.getStudent() + " - final code", JOptionPane.PLAIN_MESSAGE);
    }

    private void replay() {
        Submission s = selected();
        if (s == null) return;
        new ReplayDialog(SwingUtilities.getWindowAncestor(this), s).setVisible(true);
    }

    private void mcqSheet() {
        Submission s = selected();
        if (s == null) return;
        if (s.getMcqAnswers() == null) {
            JOptionPane.showMessageDialog(this, "This exam has no MCQ part.");
            return;
        }
        smartassessment.model.Exam exam;
        try {
            exam = FileStore.loadExam(s.getExamCode());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not load the exam file: " + ex.getMessage());
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Score: ").append(s.getMcqScore()).append(" / ").append(s.getMcqTotal()).append("\n\n");
        List<smartassessment.model.McqQuestion> qs = exam.getMcqQuestions();
        int[] ans = s.getMcqAnswers();
        for (int i = 0; i < qs.size(); i++) {
            smartassessment.model.McqQuestion q = qs.get(i);
            int chosen = i < ans.length ? ans[i] : -1;
            boolean right = chosen == q.getCorrectIndex();
            sb.append(right ? "[correct] " : (chosen < 0 ? "[no answer] " : "[wrong] "))
              .append("Q").append(i + 1).append(". ").append(q.getText()).append("\n");
            sb.append("     Student answered: ").append(chosen < 0 ? "-" : letter(chosen) + ". " + q.getOptions().get(chosen)).append("\n");
            sb.append("     Correct answer:   ").append(letter(q.getCorrectIndex())).append(". ")
              .append(q.getOptions().get(q.getCorrectIndex())).append("\n\n");
        }
        JTextArea area = new JTextArea(sb.toString(), 24, 70);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "MCQ answer sheet - " + s.getStudent(), JOptionPane.PLAIN_MESSAGE);
    }

    private static char letter(int index) { return (char) ('A' + index); }

    private void report() {
        Submission s = selected();
        if (s == null) return;
        JOptionPane.showMessageDialog(this, new JScrollPane(MonitorPanel.eventText(s.getEvents())),
                "Anti-cheat report - " + s.getStudent() + "  (warnings: " + s.getWarnings() + ")",
                JOptionPane.PLAIN_MESSAGE);
    }

    private void export() {
        String code = (String) examBox.getSelectedItem();
        if (code == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(code + "_submissions.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                FileStore.exportCsv(model.data, fc.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Exported to " + fc.getSelectedFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static class SubmissionModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] cols = {"Roll", "Name", "Batch", "Language", "Submitted at", "Warnings", "Type", "MCQ score"};
        private List<Submission> data = new ArrayList<>();

        void setData(List<Submission> d) {
            data = d;
            fireTableDataChanged();
        }

        Submission get(int i) { return data.get(i); }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override public Object getValueAt(int r, int c) {
            Submission s = data.get(r);
            switch (c) {
                case 0: return s.getStudent().getRoll();
                case 1: return s.getStudent().getName();
                case 2: return s.getStudent().getBatch();
                case 3: return s.getLanguage();
                case 4: return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(s.getSubmittedAt()));
                case 5: return s.getWarnings();
                case 6:
                    if (s.isCollectedByServer()) return "Collected by server";
                    return s.isAutoSubmitted() ? "Auto-submitted" : "Submitted by student";
                default:
                    return s.getMcqAnswers() == null ? "-" : s.getMcqScore() + " / " + s.getMcqTotal();
            }
        }
    }
}
