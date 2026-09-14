package bookloop_backend.exception;

public class BorrowRequestStateException extends RuntimeException {
    public BorrowRequestStateException(String message) {
        super(message);
    }
}
