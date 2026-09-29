package smartassessment.client;

/** Thrown when the server refuses the student's login. */
public class AuthException extends Exception {
    private static final long serialVersionUID = 1L;

    public AuthException(String message) { super(message); }
}
