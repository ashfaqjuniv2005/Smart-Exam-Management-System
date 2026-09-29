package smartassessment.ui;

import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import smartassessment.model.Exam;
import smartassessment.util.FileStore;

/** Teacher tab: create, save and load exams. */
public class ExamSetupPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JTextField titleField = new JTextField(24);
    private final JTextField codeField = new JTextField(10);
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(35, 1, 600, 1));
    private final JSpinner marksSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 1000, 1));
    private final JSpinner warningsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JCheckBox multiDisplayBox = new JCheckBox("Block multiple displays", true);
    private final JTextField forbiddenField = new JTextField(20);
    private final JTextArea rollsArea = new JTextArea(6, 14);
    private final JTextArea questionArea = new JTextArea();
    private final JLabel imageLabel = new JLabel("No image attached");
    private final JComboBox<String> savedBox = new JComboBox<>();
    private byte[] imageBytes;
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{
            "Written / Coding only", "MCQ only", "Written / Coding + MCQ"});
    private final McqEditorPanel mcqEditor = new McqEditorPanel();
    private final JTabbedPane questionTabs = new JTabbedPane();

    public ExamSetupPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // settings
        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createTitledBorder("Exam settings"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        addRow(settings, c, 0, "Exam title:", titleField, "Exam code (students type this):", codeField);
        addRow(settings, c, 1, "Duration (minutes):", durationSpinner, "Total marks:", marksSpinner);
        addRow(settings, c, 2, "Max warnings (0 = unlimited):", warningsSpinner, "", multiDisplayBox);
        addRow(settings, c, 3, "Exam type:", typeBox, "", new JLabel(""));
        c.gridx = 0; c.gridy = 4; c.gridwidth = 1;
        settings.add(new JLabel("Forbidden apps (comma separated):"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        forbiddenField.setText(String.join(", ", Exam.DEFAULT_FORBIDDEN_APPS));
        settings.add(forbiddenField, c);

        rollsArea.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY),
                "Allowed exam rolls (one per line, blank = everyone)", TitledBorder.LEFT, TitledBorder.TOP));
        JPanel north = new JPanel(new BorderLayout(8, 0));
        north.add(settings, BorderLayout.CENTER);
        north.add(new JScrollPane(rollsArea), BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        // question
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        JPanel qPanel = new JPanel(new BorderLayout(4, 4));
        qPanel.setBorder(BorderFactory.createTitledBorder("Question paper text"));
        qPanel.add(new JScrollPane(questionArea), BorderLayout.CENTER);
        JPanel imgBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton attach = new JButton("Attach image (e.g. UML diagram)...");
        JButton remove = new JButton("Remove image");
        imgBar.add(attach);
        imgBar.add(remove);
        imgBar.add(imageLabel);
        qPanel.add(imgBar, BorderLayout.SOUTH);
        questionTabs.addTab("Written / Coding question", qPanel);
        questionTabs.addTab("MCQ questions", mcqEditor);
        add(questionTabs, BorderLayout.CENTER);
        typeBox.addActionListener(e -> updateTabs());
        updateTabs();

        // buttons
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

        attach.addActionListener(e -> chooseImage());
        remove.addActionListener(e -> setImage(null, null));
        newBtn.addActionListener(e -> clearForm());
        saveBtn.addActionListener(e -> {
            Exam ex = buildExam(true);
            if (ex != null) {
                try {
                    FileStore.saveExam(ex);
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
    }

    private void addRow(JPanel p, GridBagConstraints c, int row, String l1, JComponent f1, String l2, JComponent f2) {
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.gridx = 0; p.add(new JLabel(l1), c);
        c.gridx = 1; c.weightx = 1; c.fill = f1 instanceof JTextField ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        p.add(f1, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE; p.add(new JLabel(l2), c);
        c.gridx = 3; c.weightx = 1; c.fill = f2 instanceof JTextField ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        p.add(f2, c);
    }

    /** Enables only the question tabs that belong to the selected exam type. */
    private void updateTabs() {
        int type = typeBox.getSelectedIndex();
        questionTabs.setEnabledAt(0, type != 1);
        questionTabs.setEnabledAt(1, type != 0);
        if (!questionTabs.isEnabledAt(questionTabs.getSelectedIndex())) {
            questionTabs.setSelectedIndex(type == 1 ? 1 : 0);
        }
    }

    public void refreshSaved() {
        savedBox.removeAllItems();
        for (String code : FileStore.listExamCodes()) savedBox.addItem(code);
    }

    private void loadSelected() {
        String code = (String) savedBox.getSelectedItem();
        if (code == null) return;
        try {
            Exam e = FileStore.loadExam(code);
            titleField.setText(e.getTitle());
            codeField.setText(e.getCode());
            durationSpinner.setValue(e.getDurationMinutes());
            marksSpinner.setValue(e.getTotalMarks());
            warningsSpinner.setValue(e.getMaxWarnings());
            multiDisplayBox.setSelected(e.isBlockMultiDisplay());
            forbiddenField.setText(String.join(", ", e.getForbiddenApps()));
            rollsArea.setText(String.join("\n", e.getAllowedRolls()));
            questionArea.setText(e.getQuestionText());
            setImage(e.getQuestionImage(), e.getQuestionImage() == null ? null : "(saved image)");
            typeBox.setSelectedIndex(Exam.TYPE_MCQ.equals(e.getExamType()) ? 1
                    : Exam.TYPE_BOTH.equals(e.getExamType()) ? 2 : 0);
            mcqEditor.setQuestions(e.getMcqQuestions());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not load: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearForm() {
        titleField.setText("");
        codeField.setText("");
        durationSpinner.setValue(35);
        marksSpinner.setValue(20);
        warningsSpinner.setValue(0);
        multiDisplayBox.setSelected(true);
        forbiddenField.setText(String.join(", ", Exam.DEFAULT_FORBIDDEN_APPS));
        rollsArea.setText("");
        questionArea.setText("");
        setImage(null, null);
        typeBox.setSelectedIndex(0);
        mcqEditor.setQuestions(new ArrayList<>());
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

    /**
     * Builds an Exam from the form.
     * @param showErrors show a dialog when something is missing
     * @return the exam or null if the form is incomplete
     */
    public Exam buildExam(boolean showErrors) {
        String title = titleField.getText().trim();
        String code = codeField.getText().trim();
        String question = questionArea.getText().trim();
        int type = typeBox.getSelectedIndex(); // 0 = written/coding, 1 = MCQ, 2 = both
        boolean hasCoding = type != 1;
        boolean hasMcq = type != 0;
        String problem = null;
        if (title.isEmpty()) problem = "Please enter the exam title.";
        else if (code.isEmpty()) problem = "Please enter an exam code.";
        else if (hasCoding && question.isEmpty() && imageBytes == null) problem = "Please enter the written/coding question text or attach an image.";
        else if (hasMcq && mcqEditor.getQuestions().isEmpty()) problem = "Please add at least one MCQ question (MCQ tab).";
        if (problem != null) {
            if (showErrors) JOptionPane.showMessageDialog(this, problem, "Missing information",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        Exam e = new Exam();
        e.setTitle(title);
        e.setCode(code);
        e.setQuestionText(question);
        e.setDurationMinutes((Integer) durationSpinner.getValue());
        e.setTotalMarks((Integer) marksSpinner.getValue());
        e.setMaxWarnings((Integer) warningsSpinner.getValue());
        e.setBlockMultiDisplay(multiDisplayBox.isSelected());
        e.setQuestionImage(imageBytes);
        e.setExamType(type == 1 ? Exam.TYPE_MCQ : type == 2 ? Exam.TYPE_BOTH : Exam.TYPE_CODING);
        e.setMcqQuestions(hasMcq ? mcqEditor.getQuestions() : new ArrayList<>());

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
        return e;
    }
}
