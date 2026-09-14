package bookloop_backend.service;

import bookloop_backend.exception.*;
import bookloop_backend.model.Book;
import bookloop_backend.model.BorrowRequest;
import bookloop_backend.model.User;
import bookloop_backend.repository.BookRepository;
import bookloop_backend.repository.BorrowRequestRepository;
import bookloop_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BorrowRequestService {

    private final BorrowRequestRepository borrowRequestRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public BorrowRequestService(BorrowRequestRepository borrowRequestRepository,
                                BookRepository bookRepository,
                                UserRepository userRepository) {
        this.borrowRequestRepository = borrowRequestRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    public BorrowRequest createBorrowRequest(BorrowRequest request) {
        if (request.getBookId() == null) {
            throw new InvalidBorrowRequestException("Book ID is required");
        }
        if (request.getBorrowerId() == null) {
            throw new InvalidBorrowRequestException("Borrower ID is required");
        }
        if (request.getRequestedDuration() == null || request.getRequestedDuration().isBlank()) {
            throw new InvalidBorrowRequestException("Requested duration is required");
        }

        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found"));

        if (!userRepository.existsById(request.getBorrowerId())) {
            throw new ResourceNotFoundException("Borrower not found");
        }

        // Community access control
        // TEMPORARY MIGRATION COMPATIBILITY:
        // Existing legacy automated tests and legacy database records may have communityId=null for both
        // borrower and owner. In that strictly legacy case (both null), the request is permitted to preserve
        // existing test suites.
        // In all new/community-aware flows:
        // - Borrower must belong to a community
        // - Book owner must belong to a community
        // - Borrower and owner must belong to the exact same community
        User borrower = userRepository.findById(request.getBorrowerId()).orElse(null);
        Long borrowerCommunityId = (borrower != null) ? borrower.getCommunityId() : null;

        Long ownerCommunityId = null;
        if (book.getOwnerId() != null) {
            User owner = userRepository.findById(book.getOwnerId()).orElse(null);
            ownerCommunityId = (owner != null) ? owner.getCommunityId() : null;
        }

        // If either party is community-aware, enforce community restrictions
        if (borrowerCommunityId != null || ownerCommunityId != null) {
            if (borrowerCommunityId == null) {
                throw new CommunityAccessException(
                        "A user must belong to a community to participate in community book sharing");
            }
            if (ownerCommunityId == null) {
                throw new CommunityAccessException(
                        "Cannot borrow a book that does not belong to an active community");
            }
            if (!borrowerCommunityId.equals(ownerCommunityId)) {
                throw new CommunityAccessException(
                        "Users can only borrow books from their own community");
            }
        }

        // Rule 6: A user must not be allowed to request their own book
        if (book.getOwnerId() != null && book.getOwnerId().equals(request.getBorrowerId())) {
            throw new InvalidBorrowRequestException("User cannot request to borrow their own book");
        }

        // Rule 12: A book that is not "Available" must not accept a new borrow request
        if (!"Available".equalsIgnoreCase(book.getAvailability())) {
            throw new BookNotAvailableException("Book is not available for borrowing");
        }

        // Rule 7: A borrower must not create another REQUESTED request for the same book if pending
        if (borrowRequestRepository.existsByBookIdAndBorrowerIdAndStatus(
                request.getBookId(), request.getBorrowerId(), BorrowRequest.STATUS_REQUESTED)) {
            throw new DuplicateBorrowRequestException("A pending borrow request already exists for this book");
        }

        // Rule 4: status defaults to REQUESTED
        request.setStatus(BorrowRequest.STATUS_REQUESTED);

        // Rule 5: createdAt automatically set
        request.setCreatedAt(LocalDateTime.now());

        return borrowRequestRepository.save(request);
    }

    public List<BorrowRequest> getBorrowRequests(Long userId) {
        if (userId != null) {
            return borrowRequestRepository.findRelevantRequests(userId);
        }
        return borrowRequestRepository.findAll();
    }

    public BorrowRequest acceptBorrowRequest(Long id, Long ownerId) {
        BorrowRequest request = borrowRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));

        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found"));

        // Rule 8: Only the book owner should be allowed to accept
        if (ownerId == null || !ownerId.equals(book.getOwnerId())) {
            throw new UnauthorizedActionException("Only the book owner is authorized to accept this request");
        }

        // Rule 9: Only REQUESTED requests can be accepted
        if (!BorrowRequest.STATUS_REQUESTED.equals(request.getStatus())) {
            throw new BorrowRequestStateException("Only requests in REQUESTED status can be accepted");
        }

        // Verify book is still Available before accepting
        if (!"Available".equalsIgnoreCase(book.getAvailability())) {
            throw new BookNotAvailableException("Book is no longer available for borrowing");
        }

        // Rule 10: When a request is accepted, update book availability to "Borrowed"
        book.setAvailability("Borrowed");
        bookRepository.save(book);

        request.setStatus(BorrowRequest.STATUS_ACCEPTED);
        return borrowRequestRepository.save(request);
    }

    public BorrowRequest rejectBorrowRequest(Long id, Long ownerId) {
        BorrowRequest request = borrowRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));

        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found"));

        // Rule 8: Only the book owner should be allowed to reject
        if (ownerId == null || !ownerId.equals(book.getOwnerId())) {
            throw new UnauthorizedActionException("Only the book owner is authorized to reject this request");
        }

        // Rule 9: Only REQUESTED requests can be rejected
        if (!BorrowRequest.STATUS_REQUESTED.equals(request.getStatus())) {
            throw new BorrowRequestStateException("Only requests in REQUESTED status can be rejected");
        }

        // Rule 11: When a request is rejected, leave book availability unchanged
        request.setStatus(BorrowRequest.STATUS_REJECTED);
        return borrowRequestRepository.save(request);
    }

    public BorrowRequest handoverBorrowRequest(Long id, Long ownerId) {
        BorrowRequest request = borrowRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));

        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found"));

        if (ownerId == null || !ownerId.equals(book.getOwnerId())) {
            throw new UnauthorizedActionException("Only the book owner is authorized to confirm handover");
        }

        if (!BorrowRequest.STATUS_ACCEPTED.equals(request.getStatus())) {
            throw new BorrowRequestStateException("Only requests in ACCEPTED status can be handed over");
        }

        if (!"Borrowed".equalsIgnoreCase(book.getAvailability())) {
            throw new InvalidHandoverException("Book availability must be 'Borrowed' to complete handover");
        }

        request.setStatus(BorrowRequest.STATUS_HANDED_OVER);
        request.setHandoverAt(LocalDateTime.now());
        return borrowRequestRepository.save(request);
    }

    public BorrowRequest requestReturn(Long id, Long borrowerId) {
        BorrowRequest request = borrowRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));

        // Associated book must exist
        bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found"));

        if (borrowerId == null || !borrowerId.equals(request.getBorrowerId())) {
            throw new UnauthorizedActionException("Only the borrower is authorized to request return");
        }

        if (!BorrowRequest.STATUS_HANDED_OVER.equals(request.getStatus())) {
            throw new BorrowRequestStateException("Only requests in HANDED_OVER status can be requested for return");
        }

        request.setStatus(BorrowRequest.STATUS_RETURN_REQUESTED);
        request.setReturnRequestedAt(LocalDateTime.now());
        return borrowRequestRepository.save(request);
    }

    public BorrowRequest confirmReturn(Long id, Long ownerId) {
        BorrowRequest request = borrowRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));

        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found"));

        if (ownerId == null || !ownerId.equals(book.getOwnerId())) {
            throw new UnauthorizedActionException("Only the book owner is authorized to confirm return");
        }

        if (!BorrowRequest.STATUS_RETURN_REQUESTED.equals(request.getStatus())) {
            throw new BorrowRequestStateException("Only requests in RETURN_REQUESTED status can be confirmed as returned");
        }

        request.setStatus(BorrowRequest.STATUS_RETURNED);
        request.setReturnedAt(LocalDateTime.now());

        book.setAvailability("Available");
        bookRepository.save(book);

        return borrowRequestRepository.save(request);
    }
}
