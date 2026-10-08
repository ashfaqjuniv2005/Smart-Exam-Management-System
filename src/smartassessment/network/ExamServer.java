package smartassessment.network;

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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import smartassessment.model.Exam;
import smartassessment.model.Student;
import smartassessment.model.StudentSession;
import smartassessment.model.Submission;
import smartassessment.model.Submission.CheatEvent;
import smartassessment.model.Submission.Snapshot;
import smartassessment.service.ExamService;

/** Teacher-side LAN server: authenticates students, collects snapshots, events and submissions. */
public class ExamServer {

    /** Callbacks for the teacher GUI (called from background threads). */
    public interface Listener {
        void onSessionsChanged();
        void onLog(String message);
    }

    private final Exam exam;
    private final int port;
    private final ExamService service;
    private final Listener listener;
    private final Map<String, StudentSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ClientHandler> handlers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final List<String> makeupRolls = new ArrayList<>();
    private ScheduledFuture<?> endTask;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private volatile boolean examStarted;
    private volatile boolean examEnded;
    private volatile long endTimeMillis;

    public ExamServer(Exam exam, int port, ExamService service, Listener listener) {
        this.exam = exam;
        this.port = port;
        this.service = service;
        this.listener = listener;
    }

    // ------------------------------------------------------------ lifecycle
    public void start() throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 6 && serverSocket == null; attempt++) {
            try {
                serverSocket = new ServerSocket(port); // the port may need a moment after the previous exam's server stopped
            } catch (IOException ex) {
                last = ex;
                try { Thread.sleep(400); } catch (InterruptedException ignored) { }
            }
        }
        if (serverSocket == null) throw last;
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
        beginSitting(exam.getDurationMinutes());
        log("EXAM STARTED. Duration " + exam.getDurationMinutes() + " minutes.");
        notifyChanged();
    }

    /**
     * Re-opens an ended exam with the SAME questions for students who have not submitted
     * (for example students who missed it). Students who already submitted stay locked out.
     *
     * @param minutes duration of this extra sitting
     * @param rolls   rolls allowed to join this sitting (empty = every student without a submission)
     * @return false if the exam is not in the "ended" state
     */
    public synchronized boolean restartExam(int minutes, List<String> rolls) {
        if (!examStarted || !examEnded) return false;
        examEnded = false;
        makeupRolls.clear();
        if (rolls != null) makeupRolls.addAll(rolls);
        beginSitting(minutes);
        log("EXAM RESTARTED for " + minutes + " minutes (same questions). "
                + (makeupRolls.isEmpty() ? "Open to every student who has not submitted."
                                         : "Only rolls: " + String.join(", ", makeupRolls)));
        notifyChanged();
        return true;
    }

    private void beginSitting(int minutes) {
        endTimeMillis = System.currentTimeMillis() + minutes * 60_000L;
        if (endTask != null) endTask.cancel(false);
        endTask = scheduler.schedule(this::endExam, minutes * 60L, TimeUnit.SECONDS);
    }

    public synchronized void endExam() {
        if (!examStarted || examEnded) return;
        examEnded = true;
        if (endTask != null) endTask.cancel(false);
        log("EXAM ENDED. Asking all students to submit...");
        for (ClientHandler h : handlers.values()) {
            h.send(new Message(Protocol.Type.FORCE_SUBMIT));
        }
        // After a short grace period, save whatever is left from the last snapshots.
        scheduler.schedule(this::collectPending, 8, TimeUnit.SECONDS);
        notifyChanged();
    }

    private void collectPending() {
        if (!examEnded) return; // the exam was restarted in the meantime
        for (StudentSession s : sessions.values()) {
            if (!s.isSubmitted()) {
                saveSubmission(s, true, true);
                log("Collected last snapshot of " + s.getStudent() + " (did not submit)");
            }
        }
        notifyChanged();
    }

    public void stop() {
        if (examEnded) collectPending(); // keep the work of students who did not submit
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
            h.send(new Message(Protocol.Type.BROADCAST, text));
        }
        log("Message sent to students: " + text);
    }

    // -------------------------------------------------------------- queries
    public boolean isExamStarted() { return examStarted; }
    public boolean isExamEnded() { return examEnded; }
    public Exam getExam() { return exam; }

    public long getRemainingSeconds() {
        if (!examStarted) return exam.getDurationMinutes() * 60L;
        if (examEnded) return 0;
        return Math.max(0, (endTimeMillis - System.currentTimeMillis()) / 1000);
    }

    public List<StudentSession> getSessions() {
        List<StudentSession> list = new ArrayList<>(sessions.values());
        list.sort(Comparator.comparing(s -> s.getStudent().getRoll()));
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

    private void saveSubmission(StudentSession s, boolean auto, boolean byServer) {
        try {
            service.recordSubmission(exam, s, auto, byServer);
        } catch (IOException ex) {
            log("ERROR saving submission of " + s.getStudent() + ": " + ex.getMessage());
        }
    }

    private boolean inMakeupList(String roll) {
        if (makeupRolls.isEmpty()) return true;
        for (String r : makeupRolls) {
            if (r.trim().equalsIgnoreCase(roll.trim())) return true;
        }
        return false;
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
                if (first.getType() != Protocol.Type.AUTH_REQUEST) return;
                Student student = (Student) first.getPayload();

                synchronized (ExamServer.this) {
                    StudentSession existing = student == null ? null
                            : sessions.get(student.getRoll().trim().toLowerCase());
                    String error = service.validateLogin(exam, student, existing, examStarted, examEnded);
                    if (error == null && existing == null && !inMakeupList(student.getRoll())) {
                        error = "This sitting is only for selected students.";
                    }
                    if (error != null) {
                        send(new Message(Protocol.Type.AUTH_FAIL, error));
                        log("Login refused for " + (student == null ? "?" : student) + ": " + error);
                        return;
                    }
                    key = student.getRoll().trim().toLowerCase();
                    boolean returning = existing != null;
                    session = returning ? existing
                            : new StudentSession(student, exam.getCodingQuestions().size());
                    sessions.put(key, session);
                    session.setConnected(true);
                    session.touch();
                    handlers.put(key, this);
                    if (returning) {
                        session.addEvent(new CheatEvent(CheatEvent.Kind.RECONNECTED, "Student reconnected"));
                    }
                    log((returning ? "Reconnected: " : "Logged in: ") + student);
                }
                notifyChanged();

                send(new Message(Protocol.Type.AUTH_OK, new Protocol.ExamPacket(exam.copyForStudent(),
                        getRemainingSeconds(), session.getCodes(), session.getLanguages(),
                        session.getMcqAnswers(), session.getWrittenAnswers())));

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
                case SNAPSHOT:
                    session.addSnapshot((Snapshot) m.getPayload());
                    break;
                case MCQ_ANSWERS:
                    session.setMcqAnswers((int[]) m.getPayload());
                    break;
                case WRITTEN_ANSWERS:
                    session.setWrittenAnswers((String[]) m.getPayload());
                    break;
                case CHEAT_EVENT: {
                    CheatEvent e = (CheatEvent) m.getPayload();
                    session.addEvent(e);
                    log("WARNING " + session.getStudent() + ": " + e.getKind().getLabel()
                            + (e.getDetail().isEmpty() ? "" : " (" + e.getDetail() + ")"));
                    int max = exam.getMaxWarnings();
                    if (e.isWarning() && max > 0 && session.getWarnings() > max && !session.isSubmitted()) {
                        log(session.getStudent() + " exceeded " + max + " warnings - forcing submission.");
                        send(new Message(Protocol.Type.FORCE_SUBMIT, "Too many warnings"));
                    }
                    notifyChanged();
                    break;
                }
                case SUBMIT: {
                    Submission sub = (Submission) m.getPayload();
                    if (!session.isSubmitted()) {
                        session.updateAnswers(sub.getCodeAnswers(), sub.getLanguages(), sub.getMcqAnswers(),
                                sub.getWrittenAnswers());
                        saveSubmission(session, sub.isAutoSubmitted(), false);
                        log("SUBMITTED: " + session.getStudent() + (sub.isAutoSubmitted() ? " (auto)" : ""));
                    }
                    send(new Message(Protocol.Type.SUBMIT_OK));
                    notifyChanged();
                    break;
                }
                default:
                    break;
            }
        }

        private void disconnected() {
            if (session != null && handlers.get(key) == this) {
                session.setConnected(false);
                if (!session.isSubmitted()) {
                    session.addEvent(new CheatEvent(CheatEvent.Kind.DISCONNECTED, "Connection lost"));
                    log("Disconnected: " + session.getStudent());
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
