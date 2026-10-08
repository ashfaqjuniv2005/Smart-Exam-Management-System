package smartassessment.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Shared colours and the small custom Swing components used by all windows. */
public final class Theme {
    public static final Color BLUE = new Color(0x4F, 0x6B, 0xF5);
    public static final Color DARK_BLUE = new Color(0x2F, 0x45, 0xC8);
    public static final Color BACKGROUND = Color.WHITE;
    public static final Color RED = new Color(0xC6, 0x28, 0x28);
    public static final Color GREEN = new Color(0x2E, 0x7D, 0x32);

    private Theme() { }

    /** Panel with rounded corners and a coloured border. */
    public static class RoundedPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final int arc;
        private final Color borderColor;

        public RoundedPanel(LayoutManager layout, int arc, Color borderColor) {
            super(layout);
            this.arc = arc;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Pill-shaped button used in the authentication screen. */
    public static class RoundedButton extends JButton {
        private static final long serialVersionUID = 1L;

        public RoundedButton(String text) {
            super(text);
            setFont(new Font("SansSerif", Font.BOLD, 20));
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color base = isEnabled() ? (getModel().isPressed() ? Theme.DARK_BLUE : Theme.BLUE) : Color.GRAY;
            g2.setColor(base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(getForeground());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    /** Text field with a grey hint text and a blue underline (like the authentication mock-up). */
    public static class PlaceholderField extends JTextField {
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
}
