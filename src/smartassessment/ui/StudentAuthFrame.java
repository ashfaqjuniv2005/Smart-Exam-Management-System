package smartassessment.ui;

import java.awt.*;
import java.io.IOException;
import javax.swing.*;
import smartassessment.client.AuthException;
import smartassessment.client.ExamClient;
import smartassessment.model.ExamPacket;
import smartassessment.model.StudentInfo;
import smartassessment.util.AntiCheat;

/** Student authentication screen (Name, Batch, Exam Roll, Exam Code, Server IP). */
public class StudentAuthFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int PORT = 5555;

    private final PlaceholderField nameField = new PlaceholderField("Name");
    private final PlaceholderField batchField = new PlaceholderField("Batch");
    private final PlaceholderField rollField = new PlaceholderField("Exam Roll");
    private final PlaceholderField codeField = new PlaceholderField("Exam Code");
    private final PlaceholderField serverField = new PlaceholderField("Server IP (teacher's computer)");
    private final JLabel errorLabel = new JLabel(" ", SwingConstants.CENTER);
    private final RoundedButton nextButton = new RoundedButton("Next");

    public StudentAuthFrame() {
        super("Smart Assessment - Student");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Color.WHITE);
        setLayout(new GridBagLayout());

        RoundedPanel card = new RoundedPanel(new GridBagLayout(), 40, Theme.BLUE);
        card.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));

        JLabel title = new JLabel("Student Authenticate", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(Theme.BLUE);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(8, 0, 8, 0);
        card.add(title, c);
        card.add(nameField, c);
        card.add(batchField, c);
        card.add(rollField, c);
        card.add(codeField, c);
        card.add(serverField, c);

        errorLabel.setForeground(Theme.RED);
        errorLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        card.add(errorLabel, c);

        nextButton.setPreferredSize(new Dimension(150, 50));
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0;
        bc.insets = new Insets(10, 0, 0, 0);
        card.add(nextButton, bc);

        GridBagConstraints cc = new GridBagConstraints();
        cc.insets = new Insets(30, 30, 30, 30);
        add(card, cc);

        serverField.setText("127.0.0.1");
        nextButton.addActionListener(e -> authenticate());
        getRootPane().setDefaultButton(nextButton);

        setSize(620, 700);
        setMinimumSize(new Dimension(560, 640));
        setLocationRelativeTo(null);
    }

    private void authenticate() {
        final String name = nameField.getText().trim();
        final String batch = batchField.getText().trim();
        final String roll = rollField.getText().trim();
        final String code = codeField.getText().trim();
        final String host = serverField.getText().trim();
        if (name.isEmpty() || batch.isEmpty() || roll.isEmpty() || code.isEmpty() || host.isEmpty()) {
            showError("Please fill in all the fields.");
            return;
        }
        showError(" ");
        nextButton.setEnabled(false);
        nextButton.setText("...");

        final ExamClient client = new ExamClient();
        SwingWorker<ExamPacket, Void> worker = new SwingWorker<ExamPacket, Void>() {
            @Override
            protected ExamPacket doInBackground() throws Exception {
                return client.connect(host, PORT, new StudentInfo(name, batch, roll, code));
            }

            @Override
            protected void done() {
                nextButton.setEnabled(true);
                nextButton.setText("Next");
                try {
                    ExamPacket packet = get();
                    if (packet.getExam().isBlockMultiDisplay() && AntiCheat.displayCount() > 1) {
                        client.close();
                        showError("Multiple displays detected. Disconnect the extra monitor and try again.");
                        return;
                    }
                    ExamFrame exam = new ExamFrame(client, packet, name, batch, roll);
                    exam.setVisible(true);
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof AuthException) {
                        showError(cause.getMessage());
                    } else if (cause instanceof IOException) {
                        showError("Cannot reach the server: " + cause.getMessage());
                    } else {
                        showError("Error: " + cause);
                    }
                }
            }
        };
        worker.execute();
    }

    private void showError(String msg) {
        errorLabel.setText("<html><div style='text-align:center;width:330px'>" + msg + "</div></html>");
    }
}
