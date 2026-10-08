package smartassessment.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import smartassessment.model.Question;
import smartassessment.service.ExamService;

/**
 * Teacher window to manage the questions of an exam: add MCQ, Written and Coding questions,
 * edit, remove, re-order, generate many questions automatically (for example 1000),
 * or import them from a txt / csv / pdf file.
 */
public class QuestionManagerFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final ExamService service;
    private final List<Question> questions;
    private final Runnable onChanged;

    private final DefaultListModel<Question> model = new DefaultListModel<>();
    private final JList<Question> list = new JList<>(model);
    private final JLabel totalLabel = new JLabel();

    private final JRadioButton mcqRadio = new JRadioButton("MCQ", true);
    private final JRadioButton writtenRadio = new JRadioButton("Written");
    private final JRadioButton codingRadio = new JRadioButton("Coding");
    private final JTextArea questionArea = new JTextArea(5, 30);
    private final JSpinner marksSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JLabel hintLabel = new JLabel();

    // MCQ form
    private final JPanel optionsPanel = new JPanel();
    private final ButtonGroup correctGroup = new ButtonGroup();
    private final List<JRadioButton> correctButtons = new ArrayList<>();
    private final List<JTextField> optionFields = new ArrayList<>();

    // written / coding form (optional picture)
    private final JLabel imageLabel = new JLabel("No image attached");
    private byte[] imageBytes;

    private boolean loading;

    /**
     * @param service   used to generate and import questions
     * @param questions the exam's question list (changed directly by this window)
     * @param onChanged called after every change so the caller can refresh its summary
     */
    public QuestionManagerFrame(ExamService service, List<Question> questions, Runnable onChanged) {
        super("Question Manager");
        this.service = service;
        this.questions = questions;
        this.onChanged = onChanged;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (Question q : questions) model.addElement(q);

        add(buildLeft(), BorderLayout.WEST);
        add(buildForm(), BorderLayout.CENTER);
        setOptionCount(4);
        updateTotal();
        typeChanged();

        setSize(1100, 700);
        setLocationRelativeTo(null);
    }

    // ================================================================ layout
    private JPanel buildLeft() {
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("Questions in this exam"));
        left.setPreferredSize(new Dimension(430, 100));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        left.add(new JScrollPane(list), BorderLayout.CENTER);

        JButton generate = new JButton("\u2699 Generate questions automatically...");
        generate.setFont(generate.getFont().deriveFont(Font.BOLD));
        JButton importBtn = new JButton("Import from file (txt / csv / pdf)...");
        JButton exportBtn = new JButton("Export CSV...");
        JButton sampleBtn = new JButton("Save sample files...");
        JPanel fileRow = new JPanel(new GridLayout(1, 2, 4, 4));
        fileRow.add(exportBtn);
        fileRow.add(sampleBtn);
        JPanel tools = new JPanel(new GridLayout(3, 1, 4, 4));
        tools.add(generate);
        tools.add(importBtn);
        tools.add(fileRow);

        JButton up = new JButton("Move up");
        JButton down = new JButton("Move down");
        JButton remove = new JButton("Remove");
        JButton clear = new JButton("Remove all");
        JPanel grid = new JPanel(new GridLayout(2, 2, 4, 4));
        grid.add(up);
        grid.add(down);
        grid.add(remove);
        grid.add(clear);
        JPanel south = new JPanel(new BorderLayout(4, 4));
        south.add(tools, BorderLayout.NORTH);
        south.add(grid, BorderLayout.CENTER);
        south.add(totalLabel, BorderLayout.SOUTH);
        left.add(south, BorderLayout.SOUTH);

        generate.addActionListener(e -> generate());
        importBtn.addActionListener(e -> importFromFile());
        exportBtn.addActionListener(e -> exportToCsv());
        sampleBtn.addActionListener(e -> saveSamples());
        up.addActionListener(e -> move(-1));
        down.addActionListener(e -> move(1));
        remove.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) {
                model.remove(i);
                changed();
            }
        });
        clear.addActionListener(e -> {
            if (!model.isEmpty() && JOptionPane.showConfirmDialog(this, "Remove all " + model.size()
                    + " questions?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                model.clear();
                changed();
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) loadForm(list.getSelectedValue());
        });
        return left;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new BorderLayout(6, 6));
        form.setBorder(BorderFactory.createTitledBorder("Question editor"));

        // type + marks
        ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(mcqRadio);
        typeGroup.add(writtenRadio);
        typeGroup.add(codingRadio);
        JPanel typeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        typeBar.add(new JLabel("Type:"));
        typeBar.add(mcqRadio);
        typeBar.add(writtenRadio);
        typeBar.add(codingRadio);
        typeBar.add(new JLabel("      Marks:"));
        typeBar.add(marksSpinner);
        form.add(typeBar, BorderLayout.NORTH);

        // question text + type specific part
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        JScrollPane qScroll = new JScrollPane(questionArea);
        qScroll.setBorder(BorderFactory.createTitledBorder("Question text"));

        cardPanel.add(buildMcqCard(), "MCQ");
        cardPanel.add(buildOtherCard(), "OTHER");
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(qScroll, BorderLayout.NORTH);
        center.add(cardPanel, BorderLayout.CENTER);
        form.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton addQ = new JButton("Add as new question");
        JButton updateQ = new JButton("Update selected question");
        JButton clearForm = new JButton("Clear form");
        actions.add(addQ);
        actions.add(updateQ);
        actions.add(clearForm);
        form.add(actions, BorderLayout.SOUTH);

        mcqRadio.addActionListener(e -> typeChanged());
        writtenRadio.addActionListener(e -> typeChanged());
        codingRadio.addActionListener(e -> typeChanged());
        addQ.addActionListener(e -> {
            Question q = readForm();
            if (q != null) {
                model.addElement(q);
                changed();
                clearForm();
                list.ensureIndexIsVisible(model.size() - 1);
            }
        });
        updateQ.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0) {
                JOptionPane.showMessageDialog(this, "Select a question in the list first.");
                return;
            }
            Question q = readForm();
            if (q != null) {
                model.set(i, q);
                changed();
            }
        });
        clearForm.addActionListener(e -> clearForm());
        return form;
    }

    private JPanel buildMcqCard() {
        JPanel card = new JPanel(new BorderLayout());
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createTitledBorder(
                "Option items  (select the radio button of the CORRECT answer)"));
        wrap.add(new JScrollPane(optionsPanel), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton addOpt = new JButton("+ Add option item");
        JButton removeOpt = new JButton("- Remove last option");
        buttons.add(addOpt);
        buttons.add(removeOpt);
        wrap.add(buttons, BorderLayout.SOUTH);
        card.add(wrap, BorderLayout.CENTER);
        addOpt.addActionListener(e -> setOptionCount(optionFields.size() + 1));
        removeOpt.addActionListener(e -> {
            if (optionFields.size() > 2) setOptionCount(optionFields.size() - 1);
        });
        return card;
    }

    /** Part shown for Written and Coding questions: explanation and an optional picture. */
    private JPanel buildOtherCard() {
        JPanel card = new JPanel(new BorderLayout());
        JPanel wrap = new JPanel(new BorderLayout(6, 6));
        wrap.setBorder(BorderFactory.createTitledBorder("Answer type"));
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 14f));
        wrap.add(hintLabel, BorderLayout.NORTH);
        JPanel imageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton attach = new JButton("Attach image (e.g. UML diagram)...");
        JButton remove = new JButton("Remove image");
        imageRow.add(attach);
        imageRow.add(remove);
        imageRow.add(imageLabel);
        wrap.add(imageRow, BorderLayout.CENTER);
        card.add(wrap, BorderLayout.NORTH);
        attach.addActionListener(e -> chooseImage());
        remove.addActionListener(e -> setImage(null, null));
        return card;
    }

    // ============================================================ list actions
    private void changed() {
        questions.clear();
        for (int i = 0; i < model.size(); i++) questions.add(model.get(i));
        updateTotal();
        if (onChanged != null) onChanged.run();
    }

    private void updateTotal() {
        int mcq = 0, written = 0, coding = 0, marks = 0;
        for (int i = 0; i < model.size(); i++) {
            Question q = model.get(i);
            if (q.isMcq()) mcq++;
            else if (q.isWritten()) written++;
            else coding++;
            marks += q.getMarks();
        }
        totalLabel.setText("MCQ: " + mcq + "   Written: " + written + "   Coding: " + coding
                + "   Total marks: " + marks);
    }

    private void move(int delta) {
        int i = list.getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= model.size()) return;
        Question q = model.get(i);
        model.set(i, model.get(j));
        model.set(j, q);
        list.setSelectedIndex(j);
        changed();
    }

    // ================================================================ generator
    private void generate() {
        JSpinner mcqCount = new JSpinner(new SpinnerNumberModel(600, 0, 2000, 10));
        JSpinner writtenCount = new JSpinner(new SpinnerNumberModel(200, 0, 2000, 10));
        JSpinner codingCount = new JSpinner(new SpinnerNumberModel(200, 0, 2000, 10));
        JSpinner mcqMarks = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JSpinner writtenMarks = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
        JSpinner codingMarks = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        JCheckBox shuffle = new JCheckBox("Mix the types in random order", true);
        JRadioButton add = new JRadioButton("Add to the existing questions", true);
        JRadioButton replace = new JRadioButton("Replace all existing questions");
        ButtonGroup g = new ButtonGroup();
        g.add(add);
        g.add(replace);

        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.add(new JLabel("Number of MCQ questions:"));
        p.add(mcqCount);
        p.add(new JLabel("Marks per MCQ:"));
        p.add(mcqMarks);
        p.add(new JLabel("Number of Written questions:"));
        p.add(writtenCount);
        p.add(new JLabel("Marks per Written question:"));
        p.add(writtenMarks);
        p.add(new JLabel("Number of Coding questions:"));
        p.add(codingCount);
        p.add(new JLabel("Marks per Coding question:"));
        p.add(codingMarks);
        p.add(shuffle);
        p.add(new JLabel(""));
        p.add(add);
        p.add(replace);
        p.add(new JLabel("<html><i>Example: 600 + 200 + 200 = 1000 questions.</i></html>"));
        p.add(new JLabel("<html><i>Every student gets ALL questions of the exam.</i></html>"));

        int r = JOptionPane.showConfirmDialog(this, p, "Generate Questions", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        int nMcq = (Integer) mcqCount.getValue(), nWritten = (Integer) writtenCount.getValue();
        int nCoding = (Integer) codingCount.getValue();
        if (nMcq + nWritten + nCoding == 0) {
            JOptionPane.showMessageDialog(this, "Enter at least one question.");
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        List<Question> generated;
        try {
            generated = service.generateQuestions(nMcq, nWritten, nCoding, (Integer) mcqMarks.getValue(),
                    (Integer) writtenMarks.getValue(), (Integer) codingMarks.getValue(), shuffle.isSelected());
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
        if (replace.isSelected()) model.clear();
        for (Question q : generated) model.addElement(q);
        changed();
        String msg = "Generated " + generated.size() + " questions (" + countOf(generated, Question.Type.MCQ)
                + " MCQ, " + countOf(generated, Question.Type.WRITTEN) + " Written, "
                + countOf(generated, Question.Type.CODING) + " Coding).\nAll MCQ answers were calculated by the program.";
        if (generated.size() < nMcq + nWritten + nCoding) {
            msg += "\nOnly " + generated.size() + " unique questions were possible.";
        }
        JOptionPane.showMessageDialog(this, msg, "Questions generated", JOptionPane.INFORMATION_MESSAGE);
    }

    private static int countOf(List<Question> list, Question.Type type) {
        int n = 0;
        for (Question q : list) if (q.getType() == type) n++;
        return n;
    }

    // ============================================================ import / export
    private void importFromFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose a question file (txt / csv / pdf)");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Question files (txt, csv, pdf)", "txt", "csv", "pdf"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        JSpinner mcqMarks = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JSpinner writtenMarks = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
        JSpinner codingMarks = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        JRadioButton add = new JRadioButton("Add to the existing questions", true);
        JRadioButton replace = new JRadioButton("Replace all existing questions");
        ButtonGroup g = new ButtonGroup();
        g.add(add);
        g.add(replace);
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.add(new JLabel("Marks for an MCQ without marks:"));
        p.add(mcqMarks);
        p.add(new JLabel("Marks for a Written question without marks:"));
        p.add(writtenMarks);
        p.add(new JLabel("Marks for a Coding question without marks:"));
        p.add(codingMarks);
        p.add(add);
        p.add(replace);
        if (JOptionPane.showConfirmDialog(this, p, "Import " + fc.getSelectedFile().getName(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        ExamService.ImportResult result;
        try {
            result = service.importQuestions(fc.getSelectedFile(), (Integer) mcqMarks.getValue(),
                    (Integer) writtenMarks.getValue(), (Integer) codingMarks.getValue());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read the file:\n" + ex.getMessage(),
                    "Import failed", JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
        if (result.getQuestions().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No questions were found in the file.\n"
                    + "Use \"Save sample files...\" to see the format.\n"
                    + (result.getProblems().isEmpty() ? "" : "\n" + result.getProblems().get(0)),
                    "Nothing imported", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (replace.isSelected()) model.clear();
        for (Question q : result.getQuestions()) model.addElement(q);
        changed();

        StringBuilder msg = new StringBuilder("Imported " + result.getQuestions().size() + " questions ("
                + countOf(result.getQuestions(), Question.Type.MCQ) + " MCQ, "
                + countOf(result.getQuestions(), Question.Type.WRITTEN) + " Written, "
                + countOf(result.getQuestions(), Question.Type.CODING) + " Coding).");
        if (!result.getProblems().isEmpty()) {
            msg.append("\n\nSkipped ").append(result.getProblems().size()).append(" item(s):\n");
            for (int i = 0; i < result.getProblems().size() && i < 15; i++) {
                msg.append(" - ").append(result.getProblems().get(i)).append('\n');
            }
            if (result.getProblems().size() > 15) msg.append(" ...\n");
        }
        JTextArea area = new JTextArea(msg.toString(), Math.min(20, 5 + result.getProblems().size()), 60);
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Import finished", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportToCsv() {
        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no questions to export.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("questions.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            service.exportQuestions(new ArrayList<>(questions), fc.getSelectedFile());
            JOptionPane.showMessageDialog(this, "Exported " + model.size() + " questions (with correct answers) to\n"
                    + fc.getSelectedFile() + "\nYou can import this file again later.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveSamples() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose the folder for the sample files");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            service.writeSampleFile(new File(fc.getSelectedFile(), "sample_questions.txt"));
            service.writeSampleFile(new File(fc.getSelectedFile(), "sample_questions.csv"));
            JOptionPane.showMessageDialog(this, "Saved sample_questions.txt and sample_questions.csv in\n"
                    + fc.getSelectedFile() + "\nEdit them (or copy your questions in the same format) and import."
                    + "\nA PDF works too if it contains the same text layout as the .txt sample.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // =================================================================== form
    private int defaultMarks() {
        return mcqRadio.isSelected() ? 1 : (writtenRadio.isSelected() ? 5 : 10);
    }

    private void typeChanged() {
        boolean mcq = mcqRadio.isSelected();
        cards.show(cardPanel, mcq ? "MCQ" : "OTHER");
        hintLabel.setText(writtenRadio.isSelected()
                ? "<html>Written question: the student types a normal <b>text answer</b> (no code editor). "
                  + "You mark it later.</html>"
                : "<html>Coding question: the student writes the answer in the <b>code editor</b> "
                  + "(Java / C++ / C, Run button). You mark it later.</html>");
        if (!loading) marksSpinner.setValue(defaultMarks());
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
        loading = true;
        questionArea.setText("");
        correctGroup.clearSelection();
        setOptionCount(4);
        for (JTextField tf : optionFields) tf.setText("");
        setImage(null, null);
        marksSpinner.setValue(defaultMarks());
        list.clearSelection();
        loading = false;
    }

    private void loadForm(Question q) {
        loading = true;
        mcqRadio.setSelected(q.isMcq());
        writtenRadio.setSelected(q.isWritten());
        codingRadio.setSelected(q.isCoding());
        typeChanged();
        questionArea.setText(q.getText());
        questionArea.setCaretPosition(0);
        marksSpinner.setValue(q.getMarks());
        if (q.isMcq()) {
            setOptionCount(Math.max(2, q.getOptions().size()));
            for (int i = 0; i < optionFields.size(); i++) {
                optionFields.get(i).setText(i < q.getOptions().size() ? q.getOptions().get(i) : "");
            }
            correctGroup.clearSelection();
            if (q.getCorrectIndex() >= 0 && q.getCorrectIndex() < correctButtons.size()) {
                correctButtons.get(q.getCorrectIndex()).setSelected(true);
            }
        } else {
            setImage(q.getImage(), q.getImage() == null ? null : "(saved image)");
        }
        loading = false;
    }

    /** Reads the form; shows a message and returns null when something is missing. */
    private Question readForm() {
        String text = questionArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please type the question text.");
            return null;
        }
        int marks = (Integer) marksSpinner.getValue();
        if (writtenRadio.isSelected()) return Question.written(text, marks, imageBytes);
        if (codingRadio.isSelected()) return Question.coding(text, marks, imageBytes);

        List<String> options = new ArrayList<>();
        int selected = -1, correct = -1;
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
            JOptionPane.showMessageDialog(this,
                    "Please select the correct answer (and make sure that option is not empty).");
            return null;
        }
        return Question.mcq(text, options, correct, marks);
    }

    private void chooseImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images (png, jpg, gif)", "png", "jpg", "jpeg", "gif"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                setImage(Files.readAllBytes(f.toPath()), f.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not read image: " + ex.getMessage());
            }
        }
    }

    private void setImage(byte[] bytes, String label) {
        imageBytes = bytes;
        imageLabel.setText(bytes == null ? "No image attached" : "Attached: " + label);
    }
}
