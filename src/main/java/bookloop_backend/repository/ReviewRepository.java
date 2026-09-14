package bookloop_backend.repository;

import bookloop_backend.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBorrowRequestIdAndReviewerId(Long borrowRequestId, Long reviewerId);

    List<Review> findByRevieweeId(Long revieweeId);

    List<Review> findByBorrowRequestId(Long borrowRequestId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.revieweeId = :revieweeId")
    Double getAverageRatingByRevieweeId(@Param("revieweeId") Long revieweeId);

    Long countByRevieweeId(Long revieweeId);
}
