package smartassessment.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import javax.swing.JPanel;

/** Panel with rounded corners and a coloured border. */
public class RoundedPanel extends JPanel {
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
