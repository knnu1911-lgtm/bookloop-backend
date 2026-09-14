package bookloop_backend.controller;

import bookloop_backend.config.GlobalExceptionHandler;
import bookloop_backend.exception.DuplicateReviewException;
import bookloop_backend.exception.InvalidReviewException;
import bookloop_backend.exception.ResourceNotFoundException;
import bookloop_backend.exception.UnauthorizedReviewException;
import bookloop_backend.model.Review;
import bookloop_backend.model.ReviewSummaryResponse;
import bookloop_backend.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private ReviewController reviewController;

    private Review validReview;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(reviewController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        validReview = new Review(
                1L,
                10L,
                100L,
                200L,
                5,
                "Great experience!",
                LocalDateTime.now()
        );
    }

    @Test
    void createReview_validRequest_returns201Created() throws Exception {
        when(reviewService.createReview(any(Review.class))).thenReturn(validReview);

        String payload = """
                {
                    "borrowRequestId": 10,
                    "reviewerId": 100,
                    "revieweeId": 200,
                    "rating": 5,
                    "comment": "Great experience!"
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Great experience!"))
                .andExpect(jsonPath("$.borrowRequestId").value(10))
                .andExpect(jsonPath("$.reviewerId").value(100))
                .andExpect(jsonPath("$.revieweeId").value(200));

        verify(reviewService).createReview(any(Review.class));
    }

    @Test
    void createReview_ratingOutOfRange_returns400BadRequest() throws Exception {
        String payload = """
                {
                    "borrowRequestId": 10,
                    "reviewerId": 100,
                    "revieweeId": 200,
                    "rating": 6,
                    "comment": "Invalid rating"
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.rating").value("Rating must be between 1 and 5"));

        verify(reviewService, never()).createReview(any());
    }

    @Test
    void createReview_missingRequiredFields_returns400BadRequest() throws Exception {
        String payload = """
                {
                    "comment": "Missing all IDs and rating"
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));

        verify(reviewService, never()).createReview(any());
    }

    @Test
    void createReview_unauthorizedReviewer_returns403Forbidden() throws Exception {
        when(reviewService.createReview(any(Review.class)))
                .thenThrow(new UnauthorizedReviewException("Only transaction participants can review this transaction"));

        String payload = """
                {
                    "borrowRequestId": 10,
                    "reviewerId": 999,
                    "revieweeId": 200,
                    "rating": 5,
                    "comment": "Not a participant"
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only transaction participants can review this transaction"));
    }

    @Test
    void createReview_duplicateReview_returns409Conflict() throws Exception {
        when(reviewService.createReview(any(Review.class)))
                .thenThrow(new DuplicateReviewException("A review has already been submitted by this user for this transaction"));

        String payload = """
                {
                    "borrowRequestId": 10,
                    "reviewerId": 100,
                    "revieweeId": 200,
                    "rating": 5,
                    "comment": "Duplicate attempt"
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A review has already been submitted by this user for this transaction"));
    }

    @Test
    void createReview_notFound_returns404NotFound() throws Exception {
        when(reviewService.createReview(any(Review.class)))
                .thenThrow(new ResourceNotFoundException("Borrow request not found"));

        String payload = """
                {
                    "borrowRequestId": 9999,
                    "reviewerId": 100,
                    "revieweeId": 200,
                    "rating": 5,
                    "comment": "Non-existent request"
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Borrow request not found"));
    }

    @Test
    void createReview_invalidReviewState_returns400BadRequest() throws Exception {
        when(reviewService.createReview(any(Review.class)))
                .thenThrow(new InvalidReviewException("Only completed transactions with status RETURNED can be reviewed"));

        String payload = """
                {
                    "borrowRequestId": 10,
                    "reviewerId": 100,
                    "revieweeId": 200,
                    "rating": 5,
                    "comment": "Premature review"
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only completed transactions with status RETURNED can be reviewed"));
    }

    @Test
    void getReviewsForUser_returns200Ok() throws Exception {
        Review r1 = new Review(1L, 10L, 100L, 200L, 5, "Good", LocalDateTime.now());
        Review r2 = new Review(2L, 20L, 300L, 200L, 4, "Nice", LocalDateTime.now());
        when(reviewService.getReviewsForUser(200L)).thenReturn(Arrays.asList(r1, r2));

        mockMvc.perform(get("/api/reviews/user/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[1].rating").value(4));

        verify(reviewService).getReviewsForUser(200L);
    }

    @Test
    void getReviewsForBorrowRequest_returns200Ok() throws Exception {
        Review r1 = new Review(1L, 10L, 100L, 200L, 5, "Owner review", LocalDateTime.now());
        Review r2 = new Review(2L, 10L, 200L, 100L, 5, "Borrower review", LocalDateTime.now());
        when(reviewService.getReviewsForBorrowRequest(10L)).thenReturn(Arrays.asList(r1, r2));

        mockMvc.perform(get("/api/reviews/borrow-request/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].reviewerId").value(100))
                .andExpect(jsonPath("$[1].reviewerId").value(200));

        verify(reviewService).getReviewsForBorrowRequest(10L);
    }

    @Test
    void getAverageRatingForUser_returns200Ok() throws Exception {
        ReviewSummaryResponse summary = new ReviewSummaryResponse(4.5, 6L);
        when(reviewService.getAverageRatingForUser(200L)).thenReturn(summary);

        mockMvc.perform(get("/api/reviews/user/200/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(6));

        verify(reviewService).getAverageRatingForUser(200L);
    }
}
