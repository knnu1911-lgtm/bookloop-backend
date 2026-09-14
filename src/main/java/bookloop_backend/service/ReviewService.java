package bookloop_backend.service;

import bookloop_backend.exception.DuplicateReviewException;
import bookloop_backend.exception.InvalidReviewException;
import bookloop_backend.exception.ResourceNotFoundException;
import bookloop_backend.exception.UnauthorizedReviewException;
import bookloop_backend.model.Book;
import bookloop_backend.model.BorrowRequest;
import bookloop_backend.model.Review;
import bookloop_backend.model.ReviewSummaryResponse;
import bookloop_backend.repository.BookRepository;
import bookloop_backend.repository.BorrowRequestRepository;
import bookloop_backend.repository.ReviewRepository;
import bookloop_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         BorrowRequestRepository borrowRequestRepository,
                         BookRepository bookRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    public Review createReview(Review review) {
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            throw new InvalidReviewException("Rating must be between 1 and 5");
        }

        if (review.getBorrowRequestId() == null || review.getReviewerId() == null || review.getRevieweeId() == null) {
            throw new InvalidReviewException("Borrow request ID, reviewer ID, and reviewee ID are required");
        }

        // Rule C: A user cannot review themselves
        if (review.getReviewerId().equals(review.getRevieweeId())) {
            throw new InvalidReviewException("Users cannot review themselves");
        }

        // Rule H: Verify borrow request and related book exist
        BorrowRequest borrowRequest = borrowRequestRepository.findById(review.getBorrowRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));

        Book book = bookRepository.findById(borrowRequest.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found"));

        if (!userRepository.existsById(review.getReviewerId())) {
            throw new ResourceNotFoundException("Reviewer not found");
        }

        if (!userRepository.existsById(review.getRevieweeId())) {
            throw new ResourceNotFoundException("Reviewee not found");
        }

        // Rule A: Only a COMPLETED (RETURNED) borrowing transaction can be reviewed
        if (!BorrowRequest.STATUS_RETURNED.equals(borrowRequest.getStatus())) {
            throw new InvalidReviewException("Only completed transactions with status RETURNED can be reviewed");
        }

        // Rule B: Only participants (book owner or borrower) can review
        Long ownerId = book.getOwnerId();
        Long borrowerId = borrowRequest.getBorrowerId();
        boolean isOwner = review.getReviewerId().equals(ownerId);
        boolean isBorrower = review.getReviewerId().equals(borrowerId);

        if (!isOwner && !isBorrower) {
            throw new UnauthorizedReviewException("Only transaction participants can review this transaction");
        }

        // Rule D: Reviewer must review the other participant
        if (isOwner && !review.getRevieweeId().equals(borrowerId)) {
            throw new InvalidReviewException("Owner must review the borrower of the transaction");
        }
        if (isBorrower && !review.getRevieweeId().equals(ownerId)) {
            throw new InvalidReviewException("Borrower must review the owner of the transaction");
        }

        // Rule E: Prevent duplicate reviews
        if (reviewRepository.existsByBorrowRequestIdAndReviewerId(review.getBorrowRequestId(), review.getReviewerId())) {
            throw new DuplicateReviewException("A review has already been submitted by this user for this transaction");
        }

        // Rule G: createdAt automatically set
        review.setCreatedAt(LocalDateTime.now());

        return reviewRepository.save(review);
    }

    public List<Review> getReviewsForUser(Long userId) {
        return reviewRepository.findByRevieweeId(userId);
    }

    public List<Review> getReviewsForBorrowRequest(Long borrowRequestId) {
        return reviewRepository.findByBorrowRequestId(borrowRequestId);
    }

    public ReviewSummaryResponse getAverageRatingForUser(Long userId) {
        Long count = reviewRepository.countByRevieweeId(userId);
        if (count == null || count == 0) {
            return new ReviewSummaryResponse(0.0, 0L);
        }

        Double avg = reviewRepository.getAverageRatingByRevieweeId(userId);
        double roundedAvg = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;

        return new ReviewSummaryResponse(roundedAvg, count);
    }
}
