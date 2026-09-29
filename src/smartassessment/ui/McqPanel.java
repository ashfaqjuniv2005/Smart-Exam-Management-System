package smartassessment.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import smartassessment.model.McqQuestion;

/** Student side: shows all MCQ questions with radio buttons. */
public class McqPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Called every time the student changes an answer. */
    public interface ChangeListener {
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
    private transient ChangeListener listener;
    private boolean updating;

    public McqPanel(List<McqQuestion> questions) {
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
        add(new JScrollPane(list), BorderLayout.CENTER);
        updateProgress();
    }

    private JPanel buildCard(final int index, McqQuestion q) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Theme.BLUE, 1),
                "Question " + (index + 1) + "   (" + q.getMarks() + (q.getMarks() == 1 ? " mark)" : " marks)"),
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 14)));

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
        ButtonGroup group = new ButtonGroup();
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

    public void setChangeListener(ChangeListener l) { this.listener = l; }

    public int[] getAnswers() { return answers.clone(); }

    /** Restores answers after a reconnect. */
    public void setAnswers(int[] saved) {
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
    public void setInputEnabled(boolean enabled) {
        for (List<JRadioButton> row : radios) for (JRadioButton rb : row) rb.setEnabled(enabled);
        for (JButton b : clearButtons) b.setEnabled(enabled);
    }
}
