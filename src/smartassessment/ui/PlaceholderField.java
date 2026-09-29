package smartassessment.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JTextField;

/** Text field with a grey hint text and a blue underline (like the authentication mock-up). */
public class PlaceholderField extends JTextField {
    private static final long serialVersionUID = 1L;
    private final String hint;

    public PlaceholderField(String hint) {
        this.hint = hint;
        setFont(new Font("SansSerif", Font.PLAIN, 18));
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.BLUE),
                BorderFactory.createEmptyBorder(6, 2, 6, 2)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(90, 90, 90));
            g2.setFont(getFont());
            int y = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2 - 1;
            g2.drawString(hint, getInsets().left, y);
            g2.dispose();
        }
    }
}
