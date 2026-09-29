package smartassessment.util;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Helper methods that inspect the student's computer. */
public final class AntiCheat {
    private AntiCheat() { }

    /** Number of monitors connected to this computer. */
    public static int displayCount() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
        } catch (Throwable t) {
            return 1;
        }
    }

    /** Returns the names of running programs that appear in the forbidden list. */
    public static Set<String> findForbiddenProcesses(List<String> forbidden) {
        final Set<String> found = new TreeSet<>();
        if (forbidden == null || forbidden.isEmpty()) return found;
        final Set<String> wanted = new TreeSet<>();
        for (String f : forbidden) {
            String n = normalise(f);
            if (!n.isEmpty()) wanted.add(n);
        }
        try {
            ProcessHandle.allProcesses().forEach(ph -> {
                String cmd = ph.info().command().orElse("");
                if (cmd.isEmpty()) return;
                String name = normalise(new File(cmd).getName());
                if (wanted.contains(name)) found.add(name);
            });
        } catch (Throwable ignored) {
            // Some systems do not allow listing processes; monitoring is then skipped.
        }
        return found;
    }

    private static String normalise(String s) {
        String n = s == null ? "" : s.trim().toLowerCase();
        if (n.endsWith(".exe")) n = n.substring(0, n.length() - 4);
        return n;
    }
}
