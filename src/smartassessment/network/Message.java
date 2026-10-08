package smartassessment.network;

import java.io.Serializable;

/** One network message between the student client and the teacher server. */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Protocol.Type type;
    private final String text;
    private final Serializable payload;

    public Message(Protocol.Type type) { this(type, null, null); }
    public Message(Protocol.Type type, String text) { this(type, text, null); }
    public Message(Protocol.Type type, Serializable payload) { this(type, null, payload); }

    public Message(Protocol.Type type, String text, Serializable payload) {
        this.type = type;
        this.text = text;
        this.payload = payload;
    }

    public Protocol.Type getType() { return type; }
    public String getText() { return text; }
    public Serializable getPayload() { return payload; }
}
