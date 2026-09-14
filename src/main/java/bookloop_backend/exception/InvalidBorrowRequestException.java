package bookloop_backend.exception;

public class InvalidBorrowRequestException extends RuntimeException {
    public InvalidBorrowRequestException(String message) {
        super(message);
    }
}
