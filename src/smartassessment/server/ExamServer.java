package smartassessment.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import smartassessment.model.*;
import smartassessment.util.FileStore;

/** Teacher-side LAN server: authenticates students, collects snapshots, events and submissions. */
public class ExamServer {

    /** Callbacks for the teacher GUI (called from background threads). */
    public interface Listener {
        void onSessionsChanged();
        void onLog(String message);
    }

    private final Exam exam;
    private final int port;
    private final Listener listener;
    private final Map<String, StudentSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ClientHandler> handlers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ServerSocket serverSocket;
    private volatile boolean running;
    private volatile boolean examStarted;
    private volatile boolean examEnded;
    private volatile long endTimeMillis;

    public ExamServer(Exam exam, int port, Listener listener) {
        this.exam = exam;
        this.port = port;
        this.listener = listener;
    }

    // ------------------------------------------------------------ lifecycle
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread t = new Thread(this::acceptLoop, "exam-accept");
        t.setDaemon(true);
        t.start();
        log("Server started on port " + port + " for exam " + exam.getCode());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = serverSocket.accept();
                s.setTcpNoDelay(true);
                Thread t = new Thread(new ClientHandler(s), "exam-client");
                t.setDaemon(true);
                t.start();
            } catch (IOException ex) {
                if (running) log("Accept error: " + ex.getMessage());
            }
        }
    }

    public synchronized void startExam() {
        if (examStarted) return;
        examStarted = true;
        examEnded = false;
        endTimeMillis = System.currentTimeMillis() + exam.getDurationMinutes() * 60_000L;
        scheduler.schedule(this::endExam, exam.getDurationMinutes() * 60L, TimeUnit.SECONDS);
        log("EXAM STARTED. Duration " + exam.getDurationMinutes() + " minutes.");
        notifyChanged();
    }

    public synchronized void endExam() {
        if (!examStarted || examEnded) return;
        examEnded = true;
        log("EXAM ENDED. Asking all students to submit...");
        for (ClientHandler h : handlers.values()) {
            h.send(new Message(Message.Type.FORCE_SUBMIT));
        }
        // After a short grace period, save whatever is left from the last snapshots.
        scheduler.schedule(this::collectPending, 8, TimeUnit.SECONDS);
        notifyChanged();
    }

    private void collectPending() {
        for (StudentSession s : sessions.values()) {
            if (!s.isSubmitted()) {
                saveSubmission(s, s.getLastCode(), s.getLanguage(), true, true);
                log("Collected last snapshot of " + s.getInfo() + " (did not submit)");
            }
        }
        notifyChanged();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) { }
        for (ClientHandler h : handlers.values()) h.close();
        scheduler.shutdownNow();
        log("Server stopped.");
    }

    public void broadcast(String text) {
        for (ClientHandler h : handlers.values()) {
            h.send(new Message(Message.Type.BROADCAST, text));
        }
        log("Message sent to students: " + text);
    }

    // -------------------------------------------------------------- queries
    public boolean isExamStarted() { return examStarted; }
    public boolean isExamEnded() { return examEnded; }
    public Exam getExam() { return exam; }

    public long getRemainingSeconds() {
        if (!examStarted) return exam.getDurationMinutes() * 60L;
        return Math.max(0, (endTimeMillis - System.currentTimeMillis()) / 1000);
    }

    public List<StudentSession> getSessions() {
        List<StudentSession> list = new ArrayList<>(sessions.values());
        list.sort(Comparator.comparing(s -> s.getInfo().getRoll()));
        return list;
    }

    // -------------------------------------------------------------- helpers
    private void log(String msg) {
        String line = new SimpleDateFormat("HH:mm:ss").format(new Date()) + "  " + msg;
        if (listener != null) listener.onLog(line);
    }

    private void notifyChanged() {
        if (listener != null) listener.onSessionsChanged();
    }

    private void saveSubmission(StudentSession s, String code, String lang, boolean auto, boolean byServer) {
        try {
            Submission sub = s.buildSubmission(exam.getCode(), code, lang, auto, byServer);
            if (exam.hasMcq()) {
                int n = exam.getMcqQuestions().size();
                int[] ans = new int[n];
                java.util.Arrays.fill(ans, -1);
                int[] got = s.getMcqAnswers();
                if (got != null) {
                    for (int i = 0; i < n && i < got.length; i++) ans[i] = got[i];
                }
                sub.setMcqAnswers(ans);
                sub.setMcqScore(exam.scoreMcq(ans));
                sub.setMcqTotal(exam.getMcqTotalMarks());
            }
            FileStore.saveSubmission(sub);
            s.setSubmitted(true);
        } catch (IOException ex) {
            log("ERROR saving submission of " + s.getInfo() + ": " + ex.getMessage());
        }
    }

    private String validate(StudentInfo info) {
        if (info == null || info.getName().trim().isEmpty() || info.getRoll().trim().isEmpty()
                || info.getBatch().trim().isEmpty()) {
            return "Name, batch and exam roll are required.";
        }
        if (!exam.getCode().equalsIgnoreCase(info.getExamCode().trim())) {
            return "Invalid exam code.";
        }
        StudentSession existing = sessions.get(info.getRoll().trim().toLowerCase());
        if (!exam.isRollAllowed(info.getRoll())) {
            return "You do not have access to this exam.";
        }
        if (existing == null) {
            if (!examStarted) return "The exam has not started yet. Please wait for the teacher.";
            if (examEnded) return "The exam has already ended.";
        } else {
            if (existing.isSubmitted()) return "You have already submitted this exam.";
            if (existing.isConnected()) return "This roll is already logged in on another computer.";
        }
        return null;
    }

    // ------------------------------------------------------- client handler
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private StudentSession session;
        private String key;

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                Message first = (Message) in.readObject();
                if (first.getType() != Message.Type.AUTH_REQUEST) return;
                StudentInfo info = (StudentInfo) first.getPayload();

                synchronized (ExamServer.this) {
                    String error = validate(info);
                    if (error != null) {
                        send(new Message(Message.Type.AUTH_FAIL, error));
                        log("Login refused for " + (info == null ? "?" : info) + ": " + error);
                        return;
                    }
                    key = info.getRoll().trim().toLowerCase();
                    boolean returning = sessions.containsKey(key);
                    session = sessions.computeIfAbsent(key, k -> new StudentSession(info));
                    session.handler = this;
                    session.setConnected(true);
                    session.touch();
                    handlers.put(key, this);
                    if (returning) {
                        session.addEvent(new CheatEvent(CheatEvent.Kind.RECONNECTED, "Student reconnected"));
                    }
                    log((returning ? "Reconnected: " : "Logged in: ") + info);
                }
                notifyChanged();

                send(new Message(Message.Type.AUTH_OK, new ExamPacket(exam.copyForStudent(),
                        getRemainingSeconds(), session.getLastCode(), session.getLanguage(),
                        session.getMcqAnswers())));

                while (running) {
                    Message m = (Message) in.readObject();
                    handle(m);
                }
            } catch (EOFException | SocketException ex) {
                // client closed the connection
            } catch (Exception ex) {
                log("Connection error: " + ex.getMessage());
            } finally {
                disconnected();
                close();
            }
        }

        private void handle(Message m) {
            session.touch();
            switch (m.getType()) {
                case HEARTBEAT:
                    break;
                case SNAPSHOT: {
                    Snapshot s = (Snapshot) m.getPayload();
                    if (m.getText() != null) session.setLanguage(m.getText());
                    session.addSnapshot(s);
                    break;
                }
                case CHEAT_EVENT: {
                    CheatEvent e = (CheatEvent) m.getPayload();
                    session.addEvent(e);
                    log("WARNING " + session.getInfo() + ": " + e.getKind().getLabel()
                            + (e.getDetail().isEmpty() ? "" : " (" + e.getDetail() + ")"));
                    int max = exam.getMaxWarnings();
                    if (e.isWarning() && max > 0 && session.getWarnings() > max && !session.isSubmitted()) {
                        log(session.getInfo() + " exceeded " + max + " warnings - forcing submission.");
                        send(new Message(Message.Type.FORCE_SUBMIT, "Too many warnings"));
                    }
                    notifyChanged();
                    break;
                }
                case MCQ_ANSWERS:
                    session.setMcqAnswers((int[]) m.getPayload());
                    break;
                case SUBMIT: {
                    Submission sub = (Submission) m.getPayload();
                    if (sub.getMcqAnswers() != null) session.setMcqAnswers(sub.getMcqAnswers());
                    if (!session.isSubmitted()) {
                        saveSubmission(session, sub.getFinalCode(), sub.getLanguage(),
                                sub.isAutoSubmitted(), false);
                        log("SUBMITTED: " + session.getInfo()
                                + (sub.isAutoSubmitted() ? " (auto)" : ""));
                    }
                    send(new Message(Message.Type.SUBMIT_OK));
                    notifyChanged();
                    break;
                }
                default:
                    break;
            }
        }

        private void disconnected() {
            if (session != null && session.handler == this) {
                session.setConnected(false);
                if (!session.isSubmitted()) {
                    session.addEvent(new CheatEvent(CheatEvent.Kind.DISCONNECTED, "Connection lost"));
                    log("Disconnected: " + session.getInfo());
                }
                handlers.remove(key, this);
                notifyChanged();
            }
        }

        synchronized boolean send(Message m) {
            try {
                out.writeObject(m);
                out.flush();
                out.reset();
                return true;
            } catch (Exception ex) {
                return false;
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) { }
        }
    }
}
