package smartassessment;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import smartassessment.ui.MainFrame;


/** Entry point of the Smart Assessment Application. */
public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
