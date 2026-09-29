package smartassessment.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import smartassessment.model.McqQuestion;

/** Teacher side: create the MCQ questions of an exam (question, option items, correct answer, marks). */
public class McqEditorPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final DefaultListModel<McqQuestion> listModel = new DefaultListModel<>();
    private final JList<McqQuestion> list = new JList<>(listModel);
    private final JTextArea questionArea = new JTextArea(4, 30);
    private final JPanel optionsPanel = new JPanel();
    private final JSpinner marksSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
    private final JLabel totalLabel = new JLabel();
    private final ButtonGroup correctGroup = new ButtonGroup();
    private final List<JRadioButton> correctButtons = new ArrayList<>();
    private final List<JTextField> optionFields = new ArrayList<>();

    public McqEditorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // ---- left: list of questions
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("MCQ questions in this exam"));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel leftButtons = new JPanel(new GridLayout(2, 2, 4, 4));
        JButton up = new JButton("Move up");
        JButton down = new JButton("Move down");
        JButton remove = new JButton("Remove");
        JButton clear = new JButton("Remove all");
        leftButtons.add(up);
        leftButtons.add(down);
        leftButtons.add(remove);
        leftButtons.add(clear);
        JPanel leftSouth = new JPanel(new BorderLayout(2, 4));
        leftSouth.add(leftButtons, BorderLayout.CENTER);
        leftSouth.add(totalLabel, BorderLayout.SOUTH);
        left.add(leftSouth, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(340, 100));
        add(left, BorderLayout.WEST);

        // ---- right: form
        JPanel form = new JPanel(new BorderLayout(6, 6));
        form.setBorder(BorderFactory.createTitledBorder("Question editor"));
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        JScrollPane qScroll = new JScrollPane(questionArea);
        qScroll.setBorder(BorderFactory.createTitledBorder("Question text"));
        form.add(qScroll, BorderLayout.NORTH);

        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        JPanel optionsWrap = new JPanel(new BorderLayout());
        optionsWrap.setBorder(BorderFactory.createTitledBorder("Option items  (select the radio button of the CORRECT answer)"));
        optionsWrap.add(new JScrollPane(optionsPanel), BorderLayout.CENTER);
        JPanel optButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton addOpt = new JButton("+ Add option item");
        JButton removeOpt = new JButton("- Remove last option");
        optButtons.add(addOpt);
        optButtons.add(removeOpt);
        optButtons.add(new JLabel("     Marks for this question:"));
        optButtons.add(marksSpinner);
        optionsWrap.add(optButtons, BorderLayout.SOUTH);
        form.add(optionsWrap, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton addQ = new JButton("Add as new question");
        JButton updateQ = new JButton("Update selected question");
        JButton clearForm = new JButton("Clear form");
        actions.add(addQ);
        actions.add(updateQ);
        actions.add(clearForm);
        form.add(actions, BorderLayout.SOUTH);
        add(form, BorderLayout.CENTER);

        // ---- events
        addOpt.addActionListener(e -> setOptionCount(optionFields.size() + 1));
        removeOpt.addActionListener(e -> {
            if (optionFields.size() > 2) setOptionCount(optionFields.size() - 1);
        });
        addQ.addActionListener(e -> {
            McqQuestion q = readForm();
            if (q != null) {
                listModel.addElement(q);
                list.setSelectedIndex(listModel.size() - 1);
                clearForm();
                updateTotal();
            }
        });
        updateQ.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0) {
                JOptionPane.showMessageDialog(this, "Select a question in the list first.");
                return;
            }
            McqQuestion q = readForm();
            if (q != null) {
                listModel.set(i, q);
                updateTotal();
            }
        });
        clearForm.addActionListener(e -> clearForm());
        remove.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) {
                listModel.remove(i);
                updateTotal();
            }
        });
        clear.addActionListener(e -> {
            if (!listModel.isEmpty() && JOptionPane.showConfirmDialog(this, "Remove all MCQ questions?",
                    "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                listModel.clear();
                updateTotal();
            }
        });
        up.addActionListener(e -> move(-1));
        down.addActionListener(e -> move(1));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) loadForm(list.getSelectedValue());
        });

        setOptionCount(4);
        updateTotal();
    }

    private void move(int delta) {
        int i = list.getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= listModel.size()) return;
        McqQuestion q = listModel.get(i);
        listModel.set(i, listModel.get(j));
        listModel.set(j, q);
        list.setSelectedIndex(j);
    }

    private void updateTotal() {
        int total = 0;
        for (int i = 0; i < listModel.size(); i++) total += listModel.get(i).getMarks();
        totalLabel.setText("Questions: " + listModel.size() + "     Total MCQ marks: " + total);
    }

    /** Adds or removes option rows until there are n of them. */
    private void setOptionCount(int n) {
        while (optionFields.size() < n) {
            char letter = (char) ('A' + optionFields.size());
            JRadioButton rb = new JRadioButton();
            JTextField tf = new JTextField(34);
            correctGroup.add(rb);
            correctButtons.add(rb);
            optionFields.add(tf);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            row.add(rb);
            row.add(new JLabel(letter + "."));
            row.add(tf);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            optionsPanel.add(row);
        }
        while (optionFields.size() > n) {
            int last = optionFields.size() - 1;
            correctGroup.remove(correctButtons.get(last));
            correctButtons.remove(last);
            optionFields.remove(last);
            optionsPanel.remove(optionsPanel.getComponentCount() - 1);
        }
        optionsPanel.revalidate();
        optionsPanel.repaint();
    }

    private void clearForm() {
        questionArea.setText("");
        correctGroup.clearSelection();
        setOptionCount(4);
        for (JTextField tf : optionFields) tf.setText("");
        marksSpinner.setValue(1);
        list.clearSelection();
    }

    private void loadForm(McqQuestion q) {
        questionArea.setText(q.getText());
        setOptionCount(Math.max(2, q.getOptions().size()));
        for (int i = 0; i < optionFields.size(); i++) {
            optionFields.get(i).setText(i < q.getOptions().size() ? q.getOptions().get(i) : "");
        }
        correctGroup.clearSelection();
        if (q.getCorrectIndex() >= 0 && q.getCorrectIndex() < correctButtons.size()) {
            correctButtons.get(q.getCorrectIndex()).setSelected(true);
        }
        marksSpinner.setValue(q.getMarks());
    }

    /** Reads the form; shows a message and returns null when something is missing. */
    private McqQuestion readForm() {
        String text = questionArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please type the question text.");
            return null;
        }
        List<String> options = new ArrayList<>();
        int correct = -1;
        int selected = -1;
        for (int i = 0; i < optionFields.size(); i++) {
            if (correctButtons.get(i).isSelected()) selected = i;
        }
        for (int i = 0; i < optionFields.size(); i++) {
            String t = optionFields.get(i).getText().trim();
            if (t.isEmpty()) continue;
            if (i == selected) correct = options.size();
            options.add(t);
        }
        if (options.size() < 2) {
            JOptionPane.showMessageDialog(this, "Please enter at least two option items.");
            return null;
        }
        if (correct < 0) {
            JOptionPane.showMessageDialog(this, "Please select the correct answer (and make sure that option is not empty).");
            return null;
        }
        return new McqQuestion(text, options, correct, (Integer) marksSpinner.getValue());
    }

    public List<McqQuestion> getQuestions() {
        List<McqQuestion> result = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) result.add(listModel.get(i));
        return result;
    }

    public void setQuestions(List<McqQuestion> questions) {
        listModel.clear();
        if (questions != null) for (McqQuestion q : questions) listModel.addElement(q);
        clearForm();
        updateTotal();
    }
}
