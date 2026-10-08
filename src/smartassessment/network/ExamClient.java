package smartassessment.network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import smartassessment.model.Student;

/** Student-side network connection to the teacher's server. */
public class ExamClient {

    /** Thrown when the server refuses the student's login. */
    public static class AuthException extends Exception {
        private static final long serialVersionUID = 1L;

        public AuthException(String message) { super(message); }
    }

    /** Callbacks (called from the reader thread - use SwingUtilities.invokeLater in the GUI). */
    public interface Listener {
        void onForceSubmit(String reason);
        void onBroadcast(String text);
        void onSubmitAck();
        void onConnectionChanged(boolean connected);
    }

    private String host;
    private int port;
    private Student student;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean connected;
    private volatile Listener listener;

    public void setListener(Listener listener) { this.listener = listener; }
    public boolean isConnected() { return connected; }
    public Student getStudent() { return student; }

    /** Connects and authenticates. Throws AuthException when the server refuses. */
    public synchronized Protocol.ExamPacket connect(String host, int port, Student student)
            throws IOException, AuthException {
        this.host = host;
        this.port = port;
        this.student = student;
        return open();
    }

    /** Re-opens the connection after a network failure. */
    public synchronized Protocol.ExamPacket reconnect() throws IOException, AuthException {
        return open();
    }

    private Protocol.ExamPacket open() throws IOException, AuthException {
        closeQuietly();
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), Protocol.CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            s.setSoTimeout(Protocol.HANDSHAKE_TIMEOUT_MS);
            ObjectOutputStream o = new ObjectOutputStream(s.getOutputStream());
            o.flush();
            ObjectInputStream i = new ObjectInputStream(s.getInputStream());

            o.writeObject(new Message(Protocol.Type.AUTH_REQUEST, student));
            o.flush();
            Message reply = (Message) i.readObject();
            if (reply.getType() == Protocol.Type.AUTH_FAIL) {
                throw new AuthException(reply.getText());
            }
            if (reply.getType() != Protocol.Type.AUTH_OK) {
                throw new IOException("Unexpected reply from server");
            }
            s.setSoTimeout(0);
            socket = s;
            out = o;
            in = i;
            connected = true;
            startReader(i);
            return (Protocol.ExamPacket) reply.getPayload();
        } catch (ClassNotFoundException ex) {
            try { s.close(); } catch (IOException ignored) { }
            throw new IOException("Bad server reply", ex);
        } catch (IOException | AuthException ex) {
            try { s.close(); } catch (IOException ignored) { }
            throw ex;
        }
    }

    private void startReader(final ObjectInputStream stream) {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    Message m = (Message) stream.readObject();
                    Listener l = listener;
                    if (l == null) continue;
                    switch (m.getType()) {
                        case FORCE_SUBMIT: l.onForceSubmit(m.getText()); break;
                        case BROADCAST: l.onBroadcast(m.getText()); break;
                        case SUBMIT_OK: l.onSubmitAck(); break;
                        default: break;
                    }
                }
            } catch (Exception ex) {
                // connection closed or broken
            } finally {
                synchronized (ExamClient.this) {
                    if (in == stream) {
                        connected = false;
                        Listener l = listener;
                        if (l != null) l.onConnectionChanged(false);
                    }
                }
            }
        }, "exam-client-reader");
        t.setDaemon(true);
        t.start();
    }

    /** Sends a message; returns false when the connection is down. */
    public synchronized boolean send(Message m) {
        if (!connected) return false;
        try {
            out.writeObject(m);
            out.flush();
            out.reset();
            return true;
        } catch (IOException ex) {
            connected = false;
            closeQuietly();
            Listener l = listener;
            if (l != null) l.onConnectionChanged(false);
            return false;
        }
    }

    public synchronized void close() {
        connected = false;
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) { }
        socket = null;
        in = null;
    }
}
