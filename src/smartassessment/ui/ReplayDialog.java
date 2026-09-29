package smartassessment.ui;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import smartassessment.model.CheatEvent;
import smartassessment.model.Snapshot;
import smartassessment.model.Submission;

/** Lets the teacher scrub through the saved snapshots to see how the code was developed. */
public class ReplayDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final List<Snapshot> snapshots;
    private final CodeEditorPane viewer = new CodeEditorPane();
    private final JSlider slider;
    private final JLabel infoLabel = new JLabel(" ");
    private final Timer playTimer;
    private final JButton playBtn = new JButton("\u25B6 Play");

    public ReplayDialog(Window owner, Submission sub) {
        super(owner, "Replay - " + sub.getStudent(), ModalityType.APPLICATION_MODAL);
        this.snapshots = sub.getSnapshots();
        setSize(1000, 680);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(6, 6));

        viewer.setEditable(false);
        viewer.setLanguage(sub.getLanguage());
        add(viewer.createScrollPane(), BorderLayout.CENTER);

        // events list on the right
        DefaultListModel<String> lm = new DefaultListModel<>();
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
        final List<CheatEvent> events = sub.getEvents();
        for (CheatEvent e : events) {
            lm.addElement(fmt.format(new Date(e.getTimeMillis())) + "  " + (e.isWarning() ? "\u26A0 " : "")
                    + e.getKind().getLabel() + (e.getDetail().isEmpty() ? "" : " - " + e.getDetail()));
        }
        JList<String> eventList = new JList<>(lm);
        JScrollPane evScroll = new JScrollPane(eventList);
        evScroll.setPreferredSize(new Dimension(300, 100));
        evScroll.setBorder(BorderFactory.createTitledBorder("Events (click to jump to that moment)"));
        add(evScroll, BorderLayout.EAST);

        // controls
        int max = Math.max(0, snapshots.size() - 1);
        slider = new JSlider(0, max, max);
        slider.setPaintTicks(max <= 60);
        slider.setMajorTickSpacing(Math.max(1, max / 10));
        JButton prev = new JButton("\u25C0 Prev");
        JButton next = new JButton("Next \u25B6");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttons.add(prev);
        buttons.add(next);
        buttons.add(playBtn);
        buttons.add(infoLabel);
        JPanel south = new JPanel(new BorderLayout());
        south.add(slider, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        slider.addChangeListener(e -> show(slider.getValue()));
        prev.addActionListener(e -> slider.setValue(slider.getValue() - 1));
        next.addActionListener(e -> slider.setValue(slider.getValue() + 1));
        playTimer = new Timer(700, e -> {
            if (slider.getValue() >= slider.getMaximum()) stopPlay();
            else slider.setValue(slider.getValue() + 1);
        });
        playBtn.addActionListener(e -> {
            if (playTimer.isRunning()) {
                stopPlay();
            } else {
                if (slider.getValue() >= slider.getMaximum()) slider.setValue(0);
                playTimer.start();
                playBtn.setText("\u23F8 Pause");
            }
        });
        eventList.addListSelectionListener(e -> {
            int i = eventList.getSelectedIndex();
            if (!e.getValueIsAdjusting() && i >= 0) jumpToTime(events.get(i).getTimeMillis());
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { playTimer.stop(); }
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        show(max);
    }

    private void stopPlay() {
        playTimer.stop();
        playBtn.setText("\u25B6 Play");
    }

    private void jumpToTime(long time) {
        int best = 0;
        for (int i = 0; i < snapshots.size(); i++) {
            if (snapshots.get(i).getTimeMillis() <= time) best = i;
        }
        slider.setValue(best);
    }

    private void show(int index) {
        if (snapshots.isEmpty()) {
            infoLabel.setText("No snapshots were recorded.");
            return;
        }
        index = Math.max(0, Math.min(index, snapshots.size() - 1));
        Snapshot s = snapshots.get(index);
        viewer.setCodeSilently(s.getCode());
        long start = snapshots.get(0).getTimeMillis();
        long secs = (s.getTimeMillis() - start) / 1000;
        int delta = index == 0 ? s.getCode().length()
                : s.getCode().length() - snapshots.get(index - 1).getCode().length();
        String note = s.getNote().isEmpty() ? "" : "   [" + s.getNote() + "]";
        infoLabel.setText(String.format("   Snapshot %d/%d   |   +%02d:%02d after start   |   %d characters (%+d)%s",
                index + 1, snapshots.size(), secs / 60, secs % 60, s.getCode().length(), delta, note));
        infoLabel.setForeground(Math.abs(delta) > 150 && index > 0 ? Theme.RED : Color.BLACK);
    }
}
