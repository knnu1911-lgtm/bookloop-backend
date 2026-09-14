package bookloop_backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "borrow_requests")
public class BorrowRequest {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_HANDED_OVER = "HANDED_OVER";
    public static final String STATUS_RETURN_REQUESTED = "RETURN_REQUESTED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Book ID is required")
    private Long bookId;

    @NotNull(message = "Borrower ID is required")
    private Long borrowerId;

    @NotBlank(message = "Requested duration is required")
    private String requestedDuration;

    private String message;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime handoverAt;

    private LocalDateTime returnRequestedAt;

    private LocalDateTime returnedAt;

    public BorrowRequest() {
    }

    public BorrowRequest(Long bookId, Long borrowerId, String requestedDuration, String message) {
        this.bookId = bookId;
        this.borrowerId = borrowerId;
        this.requestedDuration = requestedDuration;
        this.message = message;
        this.status = STATUS_REQUESTED;
        this.createdAt = LocalDateTime.now();
    }

    public BorrowRequest(Long id, Long bookId, Long borrowerId, String requestedDuration, String message, String status, LocalDateTime createdAt) {
        this.id = id;
        this.bookId = bookId;
        this.borrowerId = borrowerId;
        this.requestedDuration = requestedDuration;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
    }

    public BorrowRequest(Long id, Long bookId, Long borrowerId, String requestedDuration, String message, String status,
                         LocalDateTime createdAt, LocalDateTime handoverAt, LocalDateTime returnRequestedAt, LocalDateTime returnedAt) {
        this.id = id;
        this.bookId = bookId;
        this.borrowerId = borrowerId;
        this.requestedDuration = requestedDuration;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
        this.handoverAt = handoverAt;
        this.returnRequestedAt = returnRequestedAt;
        this.returnedAt = returnedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Long getBorrowerId() {
        return borrowerId;
    }

    public void setBorrowerId(Long borrowerId) {
        this.borrowerId = borrowerId;
    }

    public String getRequestedDuration() {
        return requestedDuration;
    }

    public void setRequestedDuration(String requestedDuration) {
        this.requestedDuration = requestedDuration;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getHandoverAt() {
        return handoverAt;
    }

    public void setHandoverAt(LocalDateTime handoverAt) {
        this.handoverAt = handoverAt;
    }

    public LocalDateTime getReturnRequestedAt() {
        return returnRequestedAt;
    }

    public void setReturnRequestedAt(LocalDateTime returnRequestedAt) {
        this.returnRequestedAt = returnRequestedAt;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(LocalDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }
}
