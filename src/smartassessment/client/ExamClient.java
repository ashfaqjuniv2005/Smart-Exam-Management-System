package smartassessment.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import smartassessment.model.ExamPacket;
import smartassessment.model.Message;
import smartassessment.model.StudentInfo;


public class ExamClient {

   
    public interface Listener {
        void onForceSubmit(String reason);
        void onBroadcast(String text);
        void onSubmitAck();
        void onConnectionChanged(boolean connected);
    }

    private String host;
    private int port;
    private StudentInfo info;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean connected;
    private volatile Listener listener;

    public void setListener(Listener listener) { this.listener = listener; }
    public boolean isConnected() { return connected; }
    public StudentInfo getInfo() { return info; }

   
    public synchronized ExamPacket connect(String host, int port, StudentInfo info)
            throws IOException, AuthException {
        this.host = host;
        this.port = port;
        this.info = info;
        return open();
    }

   
    public synchronized ExamPacket reconnect() throws IOException, AuthException {
        return open();
    }

    private ExamPacket open() throws IOException, AuthException {
        closeQuietly();
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), 4000);
            s.setTcpNoDelay(true);
            s.setSoTimeout(6000);
            ObjectOutputStream o = new ObjectOutputStream(s.getOutputStream());
            o.flush();
            ObjectInputStream i = new ObjectInputStream(s.getInputStream());

            o.writeObject(new Message(Message.Type.AUTH_REQUEST, info));
            o.flush();
            Message reply = (Message) i.readObject();
            if (reply.getType() == Message.Type.AUTH_FAIL) {
                throw new AuthException(reply.getText());
            }
            if (reply.getType() != Message.Type.AUTH_OK) {
                throw new IOException("Unexpected reply from server");
            }
            s.setSoTimeout(0);
            socket = s;
            out = o;
            in = i;
            connected = true;
            startReader(i);
            return (ExamPacket) reply.getPayload();
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
