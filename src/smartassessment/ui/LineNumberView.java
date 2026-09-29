package smartassessment.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

/** Line numbers shown to the left of a text component (use as JScrollPane row header). */
public class LineNumberView extends JComponent {
    private static final long serialVersionUID = 1L;
    private final JTextComponent text;

    public LineNumberView(JTextComponent text) {
        this.text = text;
        setOpaque(true);
        setBackground(new Color(0xF0, 0xF0, 0xF0));
        setForeground(new Color(0x77, 0x77, 0x77));
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
    }

    private void update() {
        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }

    private int lineCount() {
        return text.getDocument().getDefaultRootElement().getElementCount();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(text.getFont());
        int digits = Math.max(3, String.valueOf(lineCount()).length());
        return new Dimension(fm.charWidth('0') * digits + 16, text.getHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Rectangle clip = g.getClipBounds();
        g.setColor(getBackground());
        g.fillRect(clip.x, clip.y, clip.width, clip.height);
        g.setColor(getForeground());
        g.setFont(text.getFont());
        FontMetrics fm = g.getFontMetrics();

        int start = text.viewToModel2D(new Point(0, clip.y));
        int end = text.viewToModel2D(new Point(0, clip.y + clip.height));
        Element root = text.getDocument().getDefaultRootElement();
        int first = root.getElementIndex(start);
        int last = root.getElementIndex(end);
        for (int line = first; line <= last; line++) {
            try {
                Rectangle2D r = text.modelToView2D(root.getElement(line).getStartOffset());
                if (r == null) continue;
                String num = String.valueOf(line + 1);
                int x = getWidth() - fm.stringWidth(num) - 8;
                int y = (int) r.getY() + fm.getAscent();
                g.drawString(num, x, y);
            } catch (BadLocationException ignored) { }
        }
    }
}
