package smartassessment.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

/** Code editor with syntax highlighting, undo/redo and large-insert detection. */
public class CodeEditorPane extends JTextPane {
    private static final long serialVersionUID = 1L;

    /** Called when a big block of text appears in one edit (possible pasted code). */
    public interface LargeInsertListener {
        void largeInsert(int length);
    }

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null", "var"));

    private static final Set<String> C_KEYWORDS = new HashSet<>(Arrays.asList(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum",
            "extern", "float", "for", "goto", "if", "int", "long", "register", "return", "short", "signed",
            "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile",
            "while", "class", "public", "private", "protected", "virtual", "namespace", "using", "template",
            "typename", "new", "delete", "this", "true", "false", "nullptr", "bool", "try", "catch", "throw",
            "operator", "friend", "inline", "string", "cout", "cin", "endl"));

    private static final Pattern TOKENS = Pattern.compile(
            "//[^\\n]*"                              // line comment
            + "|/\\*.*?(?:\\*/|\\z)"                 // block comment
            + "|\"(?:\\\\.|[^\"\\\\\\n])*\""         // string
            + "|'(?:\\\\.|[^'\\\\\\n])*'"            // char
            + "|^[ \\t]*#[^\\n]*"                    // preprocessor
            + "|\\b\\d+(?:\\.\\d+)?[fFlLdD]?\\b"     // number
            + "|\\b[A-Za-z_]\\w*\\b",                // word
            Pattern.MULTILINE | Pattern.DOTALL);

    private final SimpleAttributeSet normal = style(Color.BLACK, false, false);
    private final SimpleAttributeSet keyword = style(new Color(0x00, 0x33, 0xB3), true, false);
    private final SimpleAttributeSet string = style(new Color(0x06, 0x7D, 0x17), false, false);
    private final SimpleAttributeSet comment = style(new Color(0x80, 0x80, 0x80), false, true);
    private final SimpleAttributeSet number = style(new Color(0x17, 0x50, 0xEB), false, false);
    private final SimpleAttributeSet preproc = style(new Color(0x9E, 0x88, 0x0D), false, false);

    private final UndoManager undo = new UndoManager();
    private Set<String> keywords = JAVA_KEYWORDS;
    private boolean cLike;
    private boolean highlightPending;
    private boolean silent;
    private LargeInsertListener largeInsertListener;

    public CodeEditorPane() {
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        setMargin(new java.awt.Insets(4, 6, 4, 6));

        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                if (!silent && largeInsertListener != null && e.getLength() > 30) {
                    largeInsertListener.largeInsert(e.getLength());
                }
                scheduleHighlight();
            }
            @Override public void removeUpdate(DocumentEvent e) { scheduleHighlight(); }
            @Override public void changedUpdate(DocumentEvent e) { /* style changes: ignore */ }
        });

        getDocument().addUndoableEditListener(e -> {
            if (e.getEdit() instanceof AbstractDocument.DefaultDocumentEvent) {
                AbstractDocument.DefaultDocumentEvent de = (AbstractDocument.DefaultDocumentEvent) e.getEdit();
                if (de.getType() == DocumentEvent.EventType.CHANGE) return;
            }
            undo.addEdit(e.getEdit());
        });

        getInputMap().put(KeyStroke.getKeyStroke("control Z"), "undo-edit");
        getInputMap().put(KeyStroke.getKeyStroke("control Y"), "redo-edit");
        getInputMap().put(KeyStroke.getKeyStroke("TAB"), "insert-4-spaces");
        getActionMap().put("undo-edit", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent e) {
                silent = true;
                try { if (undo.canUndo()) undo.undo(); } catch (CannotUndoException ignored) { }
                silent = false;
            }
        });
        getActionMap().put("redo-edit", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent e) {
                silent = true;
                try { if (undo.canRedo()) undo.redo(); } catch (CannotRedoException ignored) { }
                silent = false;
            }
        });
        getActionMap().put("insert-4-spaces", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent e) { replaceSelection("    "); }
        });
    }

    private static SimpleAttributeSet style(Color c, boolean bold, boolean italic) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, c);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        return s;
    }

    public void setLargeInsertListener(LargeInsertListener l) { this.largeInsertListener = l; }

    public void setLanguage(String language) {
        cLike = "C".equals(language) || "C++".equals(language);
        keywords = cLike ? C_KEYWORDS : JAVA_KEYWORDS;
        scheduleHighlight();
    }

    /** Returns the text exactly as stored in the document. */
    public String getCode() {
        try {
            return getDocument().getText(0, getDocument().getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    /** Replaces the whole text without triggering the large-insert warning. */
    public void setCodeSilently(String code) {
        silent = true;
        try {
            setText(code);
            setCaretPosition(0);
        } finally {
            silent = false;
        }
        undo.discardAllEdits();
        scheduleHighlight();
    }

    private void scheduleHighlight() {
        if (highlightPending) return;
        highlightPending = true;
        SwingUtilities.invokeLater(() -> {
            highlightPending = false;
            highlight();
        });
    }

    private void highlight() {
        StyledDocument doc = getStyledDocument();
        String text = getCode();
        doc.setCharacterAttributes(0, text.length(), normal, true);
        Matcher m = TOKENS.matcher(text);
        while (m.find()) {
            String t = m.group();
            SimpleAttributeSet st = null;
            char c = t.charAt(0);
            if (t.startsWith("//") || t.startsWith("/*")) st = comment;
            else if (c == '"' || c == '\'') st = string;
            else if (c == '#' || (cLike && t.trim().startsWith("#"))) st = preproc;
            else if (Character.isDigit(c)) st = number;
            else if (keywords.contains(t)) st = keyword;
            else if (t.trim().startsWith("#")) st = preproc;
            if (st != null) doc.setCharacterAttributes(m.start(), m.end() - m.start(), st, false);
        }
    }

    /** Lets the editor fill the viewport width but still scroll horizontally for long lines. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport
                && getUI().getPreferredSize(this).width <= getParent().getWidth();
    }

    /** Convenience: wraps this editor in a scroll pane with line numbers. */
    public JScrollPane createScrollPane() {
        JScrollPane sp = new JScrollPane(this);
        sp.setRowHeaderView(new LineNumberView(this));
        return sp;
    }
}
