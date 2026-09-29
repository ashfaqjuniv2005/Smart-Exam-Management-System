package smartassessment.ui;

import java.awt.*;
import javax.swing.*;

/** First screen: choose Teacher or Student. */
public class RoleFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    /** Change this password before using the program in a real exam. */
    private static final String TEACHER_PASSWORD = "teacher123";

    public RoleFrame() {
        super("Smart Assessment Application");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Color.WHITE);
        setLayout(new GridBagLayout());

        RoundedPanel card = new RoundedPanel(new GridBagLayout(), 40, Theme.BLUE);
        card.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(10, 0, 10, 0);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Smart Assessment", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(Theme.BLUE);
        JLabel sub = new JLabel("with Integrated Anti-Cheating Features", SwingConstants.CENTER);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 15));
        RoundedButton student = new RoundedButton("I am a Student");
        RoundedButton teacher = new RoundedButton("I am a Teacher");
        student.setPreferredSize(new Dimension(280, 54));
        teacher.setPreferredSize(new Dimension(280, 54));
        card.add(title, c);
        card.add(sub, c);
        card.add(Box.createVerticalStrut(10), c);
        card.add(student, c);
        card.add(teacher, c);
        add(card);

        student.addActionListener(e -> {
            new StudentAuthFrame().setVisible(true);
            dispose();
        });
        teacher.addActionListener(e -> {
            JPasswordField pw = new JPasswordField(14);
            int r = JOptionPane.showConfirmDialog(this, pw, "Teacher password",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            if (TEACHER_PASSWORD.equals(new String(pw.getPassword()))) {
                new TeacherFrame().setVisible(true);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Wrong password.", "Access denied", JOptionPane.ERROR_MESSAGE);
            }
        });

        setSize(560, 420);
        setLocationRelativeTo(null);
    }
}
