package bg.mathmission.math;

/**
 * Raised when student input is outside the supported restricted grammar or limits.
 * The message is student-facing (Bulgarian) and never contains a stack trace.
 */
public class MathInputException extends RuntimeException {

    private final String code;

    public MathInputException(String code, String message) {
        super(message, null, false, false);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
