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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BorrowRequestRepository borrowRequestRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewService reviewService;

    private Book testBook;
    private BorrowRequest returnedRequest;
    private Long ownerId = 100L;
    private Long borrowerId = 200L;
    private Long unrelatedUserId = 999L;
    private Long requestId = 10L;

    @BeforeEach
    void setUp() {
        testBook = new Book(
                "Designing Data-Intensive Applications",
                "Martin Kleppmann",
                "9781449373320",
                "Technology",
                "1st",
                "Like New",
                "Clean",
                "Available",
                "14 days",
                ownerId
        );
        testBook.setId(1L);

        returnedRequest = new BorrowRequest(
                requestId,
                testBook.getId(),
                borrowerId,
                "14 days",
                "Thanks!",
                BorrowRequest.STATUS_RETURNED,
                LocalDateTime.now().minusDays(5)
        );
    }

    private void mockValidEntities() {
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(returnedRequest));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(userRepository.existsById(borrowerId)).thenReturn(true);
    }

    @Test
    void createReview_validOwnerReviewingBorrower_success() {
        mockValidEntities();
        when(reviewRepository.existsByBorrowRequestIdAndReviewerId(requestId, ownerId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(50L);
            return r;
        });

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Great borrower, returned on time!");
        Review result = reviewService.createReview(input);

        assertNotNull(result);
        assertEquals(50L, result.getId());
        assertEquals(ownerId, result.getReviewerId());
        assertEquals(borrowerId, result.getRevieweeId());
        assertEquals(5, result.getRating());
        assertNotNull(result.getCreatedAt());
        verify(reviewRepository).save(input);
    }

    @Test
    void createReview_validBorrowerReviewingOwner_success() {
        mockValidEntities();
        when(reviewRepository.existsByBorrowRequestIdAndReviewerId(requestId, borrowerId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(51L);
            return r;
        });

        Review input = new Review(requestId, borrowerId, ownerId, 4, "Book in great condition!");
        Review result = reviewService.createReview(input);

        assertNotNull(result);
        assertEquals(51L, result.getId());
        assertEquals(borrowerId, result.getReviewerId());
        assertEquals(ownerId, result.getRevieweeId());
        assertEquals(4, result.getRating());
        assertNotNull(result.getCreatedAt());
        verify(reviewRepository).save(input);
    }

    @Test
    void createReview_ratingBoundaryMin1_success() {
        mockValidEntities();
        when(reviewRepository.existsByBorrowRequestIdAndReviewerId(requestId, ownerId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review input = new Review(requestId, ownerId, borrowerId, 1, "Poor communication");
        Review result = reviewService.createReview(input);

        assertEquals(1, result.getRating());
        verify(reviewRepository).save(input);
    }

    @Test
    void createReview_ratingBoundaryMax5_success() {
        mockValidEntities();
        when(reviewRepository.existsByBorrowRequestIdAndReviewerId(requestId, ownerId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Flawless exchange");
        Review result = reviewService.createReview(input);

        assertEquals(5, result.getRating());
        verify(reviewRepository).save(input);
    }

    @Test
    void createReview_ratingBelow1_throwsInvalidReviewException() {
        Review input = new Review(requestId, ownerId, borrowerId, 0, "Too low rating");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertEquals("Rating must be between 1 and 5", ex.getMessage());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_ratingAbove5_throwsInvalidReviewException() {
        Review input = new Review(requestId, ownerId, borrowerId, 6, "Too high rating");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertEquals("Rating must be between 1 and 5", ex.getMessage());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_ratingNull_throwsInvalidReviewException() {
        Review input = new Review(requestId, ownerId, borrowerId, null, "No rating");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertEquals("Rating must be between 1 and 5", ex.getMessage());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_missingBorrowRequestId_throwsInvalidReviewException() {
        Review input = new Review(null, ownerId, borrowerId, 5, "Missing borrow request ID");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertTrue(ex.getMessage().contains("required"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_selfReview_throwsInvalidReviewException() {
        Review input = new Review(requestId, ownerId, ownerId, 5, "Trying to review myself");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertEquals("Users cannot review themselves", ex.getMessage());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_borrowRequestNotFound_throwsResourceNotFoundException() {
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Valid review");

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reviewService.createReview(input));
        assertEquals("Borrow request not found", ex.getMessage());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_bookNotFound_throwsResourceNotFoundException() {
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(returnedRequest));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.empty());

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Valid review");

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reviewService.createReview(input));
        assertEquals("Book not found", ex.getMessage());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_reviewerNotFound_throwsResourceNotFoundException() {
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(returnedRequest));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(ownerId)).thenReturn(false);

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Valid review");

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reviewService.createReview(input));
        assertEquals("Reviewer not found", ex.getMessage());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_revieweeNotFound_throwsResourceNotFoundException() {
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(returnedRequest));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(userRepository.existsById(borrowerId)).thenReturn(false);

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Valid review");

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reviewService.createReview(input));
        assertEquals("Reviewee not found", ex.getMessage());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_statusRequested_throwsInvalidReviewException() {
        BorrowRequest nonReturned = new BorrowRequest(requestId, testBook.getId(), borrowerId, "14 days", null, BorrowRequest.STATUS_REQUESTED, LocalDateTime.now());
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(nonReturned));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(userRepository.existsById(borrowerId)).thenReturn(true);

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Early review");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertTrue(ex.getMessage().contains("RETURNED"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_statusAccepted_throwsInvalidReviewException() {
        BorrowRequest nonReturned = new BorrowRequest(requestId, testBook.getId(), borrowerId, "14 days", null, BorrowRequest.STATUS_ACCEPTED, LocalDateTime.now());
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(nonReturned));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(userRepository.existsById(borrowerId)).thenReturn(true);

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Early review");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertTrue(ex.getMessage().contains("RETURNED"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_statusHandedOver_throwsInvalidReviewException() {
        BorrowRequest nonReturned = new BorrowRequest(requestId, testBook.getId(), borrowerId, "14 days", null, BorrowRequest.STATUS_HANDED_OVER, LocalDateTime.now());
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(nonReturned));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(userRepository.existsById(borrowerId)).thenReturn(true);

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Early review");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertTrue(ex.getMessage().contains("RETURNED"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_statusReturnRequested_throwsInvalidReviewException() {
        BorrowRequest nonReturned = new BorrowRequest(requestId, testBook.getId(), borrowerId, "14 days", null, BorrowRequest.STATUS_RETURN_REQUESTED, LocalDateTime.now());
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(nonReturned));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(userRepository.existsById(borrowerId)).thenReturn(true);

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Early review");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertTrue(ex.getMessage().contains("RETURNED"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_unrelatedUserReviewer_throwsUnauthorizedReviewException() {
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(returnedRequest));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(unrelatedUserId)).thenReturn(true);
        when(userRepository.existsById(borrowerId)).thenReturn(true);

        Review input = new Review(requestId, unrelatedUserId, borrowerId, 5, "Stranger reviewing");

        UnauthorizedReviewException ex = assertThrows(UnauthorizedReviewException.class,
                () -> reviewService.createReview(input));
        assertTrue(ex.getMessage().contains("participants"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_ownerReviewingWrongUser_throwsInvalidReviewException() {
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(returnedRequest));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(userRepository.existsById(unrelatedUserId)).thenReturn(true);

        Review input = new Review(requestId, ownerId, unrelatedUserId, 5, "Owner reviewing non-borrower");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertTrue(ex.getMessage().contains("Owner must review the borrower"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_borrowerReviewingWrongUser_throwsInvalidReviewException() {
        when(borrowRequestRepository.findById(requestId)).thenReturn(Optional.of(returnedRequest));
        when(bookRepository.findById(testBook.getId())).thenReturn(Optional.of(testBook));
        when(userRepository.existsById(borrowerId)).thenReturn(true);
        when(userRepository.existsById(unrelatedUserId)).thenReturn(true);

        Review input = new Review(requestId, borrowerId, unrelatedUserId, 5, "Borrower reviewing non-owner");

        InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                () -> reviewService.createReview(input));
        assertTrue(ex.getMessage().contains("Borrower must review the owner"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_duplicateReview_throwsDuplicateReviewException() {
        mockValidEntities();
        when(reviewRepository.existsByBorrowRequestIdAndReviewerId(requestId, ownerId)).thenReturn(true);

        Review input = new Review(requestId, ownerId, borrowerId, 5, "Second review attempt");

        DuplicateReviewException ex = assertThrows(DuplicateReviewException.class,
                () -> reviewService.createReview(input));
        assertTrue(ex.getMessage().contains("already been submitted"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void getReviewsForUser_returnsReviews() {
        Review r1 = new Review(requestId, ownerId, borrowerId, 5, "Great borrower");
        Review r2 = new Review(20L, 300L, borrowerId, 4, "Good experience");
        when(reviewRepository.findByRevieweeId(borrowerId)).thenReturn(Arrays.asList(r1, r2));

        List<Review> reviews = reviewService.getReviewsForUser(borrowerId);

        assertEquals(2, reviews.size());
        verify(reviewRepository).findByRevieweeId(borrowerId);
    }

    @Test
    void getReviewsForBorrowRequest_returnsReviews() {
        Review r1 = new Review(requestId, ownerId, borrowerId, 5, "Owner to borrower");
        Review r2 = new Review(requestId, borrowerId, ownerId, 4, "Borrower to owner");
        when(reviewRepository.findByBorrowRequestId(requestId)).thenReturn(Arrays.asList(r1, r2));

        List<Review> reviews = reviewService.getReviewsForBorrowRequest(requestId);

        assertEquals(2, reviews.size());
        verify(reviewRepository).findByBorrowRequestId(requestId);
    }

    @Test
    void getAverageRatingForUser_withReviews_returnsRoundedAverageAndCount() {
        when(reviewRepository.countByRevieweeId(borrowerId)).thenReturn(3L);
        when(reviewRepository.getAverageRatingByRevieweeId(borrowerId)).thenReturn(4.666666667);

        ReviewSummaryResponse summary = reviewService.getAverageRatingForUser(borrowerId);

        assertEquals(4.7, summary.getAverageRating());
        assertEquals(3L, summary.getReviewCount());
    }

    @Test
    void getAverageRatingForUser_noReviews_returnsZeroAverageAndZeroCount() {
        when(reviewRepository.countByRevieweeId(borrowerId)).thenReturn(0L);

        ReviewSummaryResponse summary = reviewService.getAverageRatingForUser(borrowerId);

        assertEquals(0.0, summary.getAverageRating());
        assertEquals(0L, summary.getReviewCount());
        verify(reviewRepository, never()).getAverageRatingByRevieweeId(any());
    }
}
