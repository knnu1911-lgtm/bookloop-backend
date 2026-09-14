package bookloop_backend.controller;

import bookloop_backend.model.Review;
import bookloop_backend.model.ReviewSummaryResponse;
import bookloop_backend.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<Review> createReview(@Valid @RequestBody Review review) {
        Review savedReview = reviewService.createReview(review);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedReview);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Review>> getReviewsForUser(@PathVariable Long userId) {
        List<Review> reviews = reviewService.getReviewsForUser(userId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/borrow-request/{borrowRequestId}")
    public ResponseEntity<List<Review>> getReviewsForBorrowRequest(@PathVariable Long borrowRequestId) {
        List<Review> reviews = reviewService.getReviewsForBorrowRequest(borrowRequestId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<ReviewSummaryResponse> getAverageRatingForUser(@PathVariable Long userId) {
        ReviewSummaryResponse summary = reviewService.getAverageRatingForUser(userId);
        return ResponseEntity.ok(summary);
    }
}
