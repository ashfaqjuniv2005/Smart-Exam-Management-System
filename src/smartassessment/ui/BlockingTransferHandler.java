package smartassessment.ui;

import java.awt.datatransfer.Clipboard;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

/** Blocks copy, cut, paste and drag & drop, and reports each attempt. */
public class BlockingTransferHandler extends TransferHandler {
    private static final long serialVersionUID = 1L;

    /** Receives the blocked attempts. */
    public interface Reporter {
        void pasteAttempt();
        void copyAttempt();
    }

    private final transient Reporter reporter;

    public BlockingTransferHandler(Reporter reporter) { this.reporter = reporter; }

    @Override
    public int getSourceActions(JComponent c) { return COPY_OR_MOVE; }

    @Override
    public void exportToClipboard(JComponent comp, Clipboard clip, int action) {
        if (reporter != null) reporter.copyAttempt();
        // nothing is copied
    }

    @Override
    public boolean canImport(TransferSupport support) { return false; }

    @Override
    public boolean importData(TransferSupport support) {
        if (reporter != null) reporter.pasteAttempt();
        return false;
    }
}
