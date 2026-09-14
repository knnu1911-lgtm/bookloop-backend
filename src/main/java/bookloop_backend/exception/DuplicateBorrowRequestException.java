package bookloop_backend.exception;

public class DuplicateBorrowRequestException extends RuntimeException {
    public DuplicateBorrowRequestException(String message) {
        super(message);
    }
}
