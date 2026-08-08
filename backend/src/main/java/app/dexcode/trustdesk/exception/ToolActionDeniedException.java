package app.dexcode.trustdesk.exception;

public class ToolActionDeniedException extends RuntimeException {
    public ToolActionDeniedException(String message) {
        super(message);
    }
}
