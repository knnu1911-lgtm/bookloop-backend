package bookloop_backend.exception;

public class UnauthorizedReviewException extends RuntimeException {
    public UnauthorizedReviewException(String message) {
        super(message);
    }
}
