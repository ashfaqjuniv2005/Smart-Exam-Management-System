package smartassessment.model;

import java.io.Serializable;

/** Network message exchanged between the student client and teacher server. */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        AUTH_REQUEST, AUTH_OK, AUTH_FAIL,
        SNAPSHOT, CHEAT_EVENT, HEARTBEAT,
        SUBMIT, SUBMIT_OK,
        FORCE_SUBMIT, BROADCAST, MCQ_ANSWERS
    }

    private final Type type;
    private final String text;
    private final Serializable payload;

    public Message(Type type) { this(type, null, null); }
    public Message(Type type, String text) { this(type, text, null); }
    public Message(Type type, Serializable payload) { this(type, null, payload); }

    public Message(Type type, String text, Serializable payload) {
        this.type = type;
        this.text = text;
        this.payload = payload;
    }

    public Type getType() { return type; }
    public String getText() { return text; }
    public Serializable getPayload() { return payload; }
}
