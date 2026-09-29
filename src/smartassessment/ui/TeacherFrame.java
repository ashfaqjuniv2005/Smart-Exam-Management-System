package smartassessment.ui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

/** Main window of the teacher: exam setup, live monitor and submissions. */
public class TeacherFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    public TeacherFrame() {
        super("Smart Assessment - Teacher Panel");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        final ExamSetupPanel setup = new ExamSetupPanel();
        final MonitorPanel monitor = new MonitorPanel(setup);
        final SubmissionsPanel submissions = new SubmissionsPanel();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("1. Exam Setup", setup);
        tabs.addTab("2. Live Monitor", monitor);
        tabs.addTab("3. Submissions & Analytics", submissions);
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
}
