package bookloop_backend.repository;

import bookloop_backend.model.BorrowRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BorrowRequestRepository extends JpaRepository<BorrowRequest, Long> {

    boolean existsByBookIdAndBorrowerIdAndStatus(Long bookId, Long borrowerId, String status);

    List<BorrowRequest> findByBorrowerId(Long borrowerId);

    List<BorrowRequest> findByBookId(Long bookId);

    @Query("SELECT r FROM BorrowRequest r WHERE r.borrowerId = :userId OR r.bookId IN (SELECT b.id FROM Book b WHERE b.ownerId = :userId) ORDER BY r.createdAt DESC")
    List<BorrowRequest> findRelevantRequests(@Param("userId") Long userId);
}
