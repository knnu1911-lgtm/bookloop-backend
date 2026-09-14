package bookloop_backend.service;

import bookloop_backend.exception.*;
import bookloop_backend.model.Book;
import bookloop_backend.model.BorrowRequest;
import bookloop_backend.model.User;
import bookloop_backend.repository.BookRepository;
import bookloop_backend.repository.BorrowRequestRepository;
import bookloop_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BorrowRequestServiceTest {

    @Mock
    private BorrowRequestRepository borrowRequestRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BorrowRequestService borrowRequestService;

    private Book availableBook;
    private Book unavailableBook;
    private BorrowRequest sampleRequest;

    @BeforeEach
    void setUp() {
        availableBook = new Book(
                "Designing Data-Intensive Applications",
                "Martin Kleppmann",
                "9781449373320",
                "Technology",
                "1st",
                "Like New",
                "Clean",
                "Available",
                "14 days",
                100L // Owner is user 100
        );
        availableBook.setId(1L);

        unavailableBook = new Book(
                "Clean Code",
                "Robert C. Martin",
                "9780132350884",
                "Technology",
                "1st",
                "Good",
                "None",
                "Borrowed",
                "7 days",
                100L
        );
        unavailableBook.setId(2L);

        sampleRequest = new BorrowRequest(
                1L, // bookId
                200L, // borrowerId (different from owner 100)
                "14 days",
                "Would love to read this!"
        );
        sampleRequest.setId(10L);
    }

    // 1. Create valid request
    @Test
    void testCreateBorrowRequest_Success() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(200L)).thenReturn(true);
        when(borrowRequestRepository.existsByBookIdAndBorrowerIdAndStatus(1L, 200L, BorrowRequest.STATUS_REQUESTED)).thenReturn(false);
        when(borrowRequestRepository.save(any(BorrowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BorrowRequest created = borrowRequestService.createBorrowRequest(sampleRequest);

        assertNotNull(created);
        assertEquals(BorrowRequest.STATUS_REQUESTED, created.getStatus());
        assertNotNull(created.getCreatedAt());
        assertEquals(1L, created.getBookId());
        assertEquals(200L, created.getBorrowerId());
        verify(borrowRequestRepository, times(1)).save(sampleRequest);
    }

    // 2. Reject request for own book
    @Test
    void testCreateBorrowRequest_OwnBook() {
        BorrowRequest ownBookRequest = new BorrowRequest(1L, 100L, "14 days", "Self borrow");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(100L)).thenReturn(true);

        InvalidBorrowRequestException ex = assertThrows(InvalidBorrowRequestException.class, () ->
                borrowRequestService.createBorrowRequest(ownBookRequest)
        );

        assertEquals("User cannot request to borrow their own book", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    // 3. Reject duplicate pending request
    @Test
    void testCreateBorrowRequest_DuplicatePendingRequest() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(200L)).thenReturn(true);
        when(borrowRequestRepository.existsByBookIdAndBorrowerIdAndStatus(1L, 200L, BorrowRequest.STATUS_REQUESTED)).thenReturn(true);

        DuplicateBorrowRequestException ex = assertThrows(DuplicateBorrowRequestException.class, () ->
                borrowRequestService.createBorrowRequest(sampleRequest)
        );

        assertEquals("A pending borrow request already exists for this book", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    // 4. Reject request for unavailable book
    @Test
    void testCreateBorrowRequest_UnavailableBook() {
        BorrowRequest req = new BorrowRequest(2L, 200L, "7 days", "Request unavailable book");

        when(bookRepository.findById(2L)).thenReturn(Optional.of(unavailableBook));
        when(userRepository.existsById(200L)).thenReturn(true);

        BookNotAvailableException ex = assertThrows(BookNotAvailableException.class, () ->
                borrowRequestService.createBorrowRequest(req)
        );

        assertEquals("Book is not available for borrowing", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testCreateBorrowRequest_BookNotFound() {
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());
        BorrowRequest req = new BorrowRequest(999L, 200L, "7 days", "Request missing book");

        assertThrows(ResourceNotFoundException.class, () ->
                borrowRequestService.createBorrowRequest(req)
        );
    }

    @Test
    void testCreateBorrowRequest_BorrowerNotFound() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(999L)).thenReturn(false);
        BorrowRequest req = new BorrowRequest(1L, 999L, "7 days", "Borrower missing");

        assertThrows(ResourceNotFoundException.class, () ->
                borrowRequestService.createBorrowRequest(req)
        );
    }

    // 5. Accept request by actual owner
    @Test
    void testAcceptBorrowRequest_Success() {
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(borrowRequestRepository.save(any(BorrowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BorrowRequest accepted = borrowRequestService.acceptBorrowRequest(10L, 100L); // actual owner is 100L

        assertNotNull(accepted);
        assertEquals(BorrowRequest.STATUS_ACCEPTED, accepted.getStatus());
        assertEquals("Borrowed", availableBook.getAvailability());
        verify(bookRepository, times(1)).save(availableBook);
        verify(borrowRequestRepository, times(1)).save(sampleRequest);
    }

    // Accept fails if book is no longer available
    @Test
    void testAcceptBorrowRequest_BookNoLongerAvailable() {
        availableBook.setAvailability("Borrowed");
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));

        BookNotAvailableException ex = assertThrows(BookNotAvailableException.class, () ->
                borrowRequestService.acceptBorrowRequest(10L, 100L)
        );

        assertEquals("Book is no longer available for borrowing", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    // 6. Reject request by actual owner
    @Test
    void testRejectBorrowRequest_Success() {
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(borrowRequestRepository.save(any(BorrowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BorrowRequest rejected = borrowRequestService.rejectBorrowRequest(10L, 100L); // actual owner is 100L

        assertNotNull(rejected);
        assertEquals(BorrowRequest.STATUS_REJECTED, rejected.getStatus());
        assertEquals("Available", availableBook.getAvailability()); // unchanged
        verify(bookRepository, never()).save(availableBook);
        verify(borrowRequestRepository, times(1)).save(sampleRequest);
    }

    // 7. Reject accept/reject attempt by non-owner
    @Test
    void testAcceptBorrowRequest_AttemptByNonOwner() {
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                borrowRequestService.acceptBorrowRequest(10L, 999L) // 999L is not owner 100L
        );

        assertEquals("Only the book owner is authorized to accept this request", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testRejectBorrowRequest_AttemptByNonOwner() {
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                borrowRequestService.rejectBorrowRequest(10L, 999L) // 999L is not owner 100L
        );

        assertEquals("Only the book owner is authorized to reject this request", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    // 8. Reject accept/reject of an already processed request
    @Test
    void testAcceptBorrowRequest_AlreadyProcessed() {
        sampleRequest.setStatus(BorrowRequest.STATUS_ACCEPTED);
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));

        BorrowRequestStateException ex = assertThrows(BorrowRequestStateException.class, () ->
                borrowRequestService.acceptBorrowRequest(10L, 100L)
        );

        assertEquals("Only requests in REQUESTED status can be accepted", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testRejectBorrowRequest_AlreadyProcessed() {
        sampleRequest.setStatus(BorrowRequest.STATUS_REJECTED);
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));

        BorrowRequestStateException ex = assertThrows(BorrowRequestStateException.class, () ->
                borrowRequestService.rejectBorrowRequest(10L, 100L)
        );

        assertEquals("Only requests in REQUESTED status can be rejected", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testGetBorrowRequests_WithUserId() {
        when(borrowRequestRepository.findRelevantRequests(100L)).thenReturn(Arrays.asList(sampleRequest));

        List<BorrowRequest> requests = borrowRequestService.getBorrowRequests(100L);

        assertEquals(1, requests.size());
        verify(borrowRequestRepository, times(1)).findRelevantRequests(100L);
    }

    @Test
    void testGetBorrowRequests_WithoutUserId() {
        when(borrowRequestRepository.findAll()).thenReturn(Arrays.asList(sampleRequest));

        List<BorrowRequest> requests = borrowRequestService.getBorrowRequests(null);

        assertEquals(1, requests.size());
        verify(borrowRequestRepository, times(1)).findAll();
    }

    // Handover Tests
    @Test
    void testHandoverBorrowRequest_Success() {
        sampleRequest.setStatus(BorrowRequest.STATUS_ACCEPTED);
        unavailableBook.setId(1L); // book with "Borrowed" availability
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(unavailableBook));
        when(borrowRequestRepository.save(any(BorrowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BorrowRequest handedOver = borrowRequestService.handoverBorrowRequest(10L, 100L);

        assertNotNull(handedOver);
        assertEquals(BorrowRequest.STATUS_HANDED_OVER, handedOver.getStatus());
        assertNotNull(handedOver.getHandoverAt());
        assertEquals("Borrowed", unavailableBook.getAvailability());
        verify(borrowRequestRepository, times(1)).save(sampleRequest);
    }

    @Test
    void testHandoverBorrowRequest_NonOwner_Forbidden() {
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(unavailableBook));

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                borrowRequestService.handoverBorrowRequest(10L, 999L)
        );

        assertEquals("Only the book owner is authorized to confirm handover", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testHandoverBorrowRequest_StatusNotAccepted_ThrowsException() {
        sampleRequest.setStatus(BorrowRequest.STATUS_REQUESTED);
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(unavailableBook));

        BorrowRequestStateException ex = assertThrows(BorrowRequestStateException.class, () ->
                borrowRequestService.handoverBorrowRequest(10L, 100L)
        );

        assertEquals("Only requests in ACCEPTED status can be handed over", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testHandoverBorrowRequest_BookNotBorrowed_ThrowsException() {
        sampleRequest.setStatus(BorrowRequest.STATUS_ACCEPTED);
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook)); // availability is "Available"

        InvalidHandoverException ex = assertThrows(InvalidHandoverException.class, () ->
                borrowRequestService.handoverBorrowRequest(10L, 100L)
        );

        assertEquals("Book availability must be 'Borrowed' to complete handover", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testHandoverBorrowRequest_NotFound() {
        when(borrowRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                borrowRequestService.handoverBorrowRequest(999L, 100L)
        );
    }

    // Return Request Tests
    @Test
    void testRequestReturn_Success() {
        sampleRequest.setStatus(BorrowRequest.STATUS_HANDED_OVER);
        sampleRequest.setHandoverAt(LocalDateTime.now().minusDays(3));
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(unavailableBook));
        when(borrowRequestRepository.save(any(BorrowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BorrowRequest returnRequested = borrowRequestService.requestReturn(10L, 200L); // borrower is 200L

        assertNotNull(returnRequested);
        assertEquals(BorrowRequest.STATUS_RETURN_REQUESTED, returnRequested.getStatus());
        assertNotNull(returnRequested.getReturnRequestedAt());
        verify(borrowRequestRepository, times(1)).save(sampleRequest);
    }

    @Test
    void testRequestReturn_NonBorrower_Forbidden() {
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(unavailableBook));

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                borrowRequestService.requestReturn(10L, 999L)
        );

        assertEquals("Only the borrower is authorized to request return", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testRequestReturn_StatusNotHandedOver_ThrowsException() {
        sampleRequest.setStatus(BorrowRequest.STATUS_ACCEPTED);
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(unavailableBook));

        BorrowRequestStateException ex = assertThrows(BorrowRequestStateException.class, () ->
                borrowRequestService.requestReturn(10L, 200L)
        );

        assertEquals("Only requests in HANDED_OVER status can be requested for return", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testRequestReturn_NotFound() {
        when(borrowRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                borrowRequestService.requestReturn(999L, 200L)
        );
    }

    // Return Confirmation Tests
    @Test
    void testConfirmReturn_Success() {
        sampleRequest.setStatus(BorrowRequest.STATUS_RETURN_REQUESTED);
        unavailableBook.setId(1L);
        unavailableBook.setAvailability("Borrowed");
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(unavailableBook));
        when(borrowRequestRepository.save(any(BorrowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BorrowRequest confirmed = borrowRequestService.confirmReturn(10L, 100L); // owner is 100L

        assertNotNull(confirmed);
        assertEquals(BorrowRequest.STATUS_RETURNED, confirmed.getStatus());
        assertNotNull(confirmed.getReturnedAt());
        assertEquals("Available", unavailableBook.getAvailability());
        verify(bookRepository, times(1)).save(unavailableBook);
        verify(borrowRequestRepository, times(1)).save(sampleRequest);
    }

    @Test
    void testConfirmReturn_NonOwner_Forbidden() {
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(unavailableBook));

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                borrowRequestService.confirmReturn(10L, 999L)
        );

        assertEquals("Only the book owner is authorized to confirm return", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testConfirmReturn_StatusNotReturnRequested_ThrowsException() {
        sampleRequest.setStatus(BorrowRequest.STATUS_HANDED_OVER);
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(unavailableBook));

        BorrowRequestStateException ex = assertThrows(BorrowRequestStateException.class, () ->
                borrowRequestService.confirmReturn(10L, 100L)
        );

        assertEquals("Only requests in RETURN_REQUESTED status can be confirmed as returned", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    @Test
    void testConfirmReturn_NotFound() {
        when(borrowRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                borrowRequestService.confirmReturn(999L, 100L)
        );
    }

    // =========================================================================
    // Community enforcement tests (Borrow Request rule #6)
    // =========================================================================

    /**
     * MIGRATION COMPATIBILITY: When both borrower and owner have null communityId
     * (legacy users), the borrow request must still be allowed.
     * This preserves existing test data and test cases.
     */
    @Test
    void testCreateBorrowRequest_BothNullCommunity_LegacyCompatibility_Allowed() {
        // Both owner (100L) and borrower (200L) have no communityId — legacy case
        User borrower = new User("Borrower", "borrower@example.com", "hash", "Legacy");
        borrower.setCommunityId(null);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook)); // owner=100
        when(userRepository.existsById(200L)).thenReturn(true);
        // findById for community check: borrower has null communityId
        when(userRepository.findById(200L)).thenReturn(Optional.of(borrower));
        // owner lookup: also null communityId
        User owner = new User("Owner", "owner@example.com", "hash", "Legacy");
        owner.setCommunityId(null);
        when(userRepository.findById(100L)).thenReturn(Optional.of(owner));
        when(borrowRequestRepository.existsByBookIdAndBorrowerIdAndStatus(
                1L, 200L, BorrowRequest.STATUS_REQUESTED)).thenReturn(false);
        when(borrowRequestRepository.save(any(BorrowRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Must NOT throw — migration compatibility
        BorrowRequest result = borrowRequestService.createBorrowRequest(sampleRequest);
        assertNotNull(result);
        assertEquals(BorrowRequest.STATUS_REQUESTED, result.getStatus());
    }

    /**
     * Cross-community borrow must be rejected when both parties have non-null communityIds
     * that differ from each other.
     */
    @Test
    void testCreateBorrowRequest_CrossCommunity_Rejected() {
        // borrower is in community 1, owner is in community 2
        User borrower = new User("Borrower", "borrower@example.com", "hash", "CommunityA");
        borrower.setCommunityId(1L);

        User owner = new User("Owner", "owner@example.com", "hash", "CommunityB");
        owner.setCommunityId(2L); // different community

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook)); // owner=100
        when(userRepository.existsById(200L)).thenReturn(true);
        when(userRepository.findById(200L)).thenReturn(Optional.of(borrower));
        when(userRepository.findById(100L)).thenReturn(Optional.of(owner));

        CommunityAccessException ex = assertThrows(CommunityAccessException.class, () ->
                borrowRequestService.createBorrowRequest(sampleRequest));
        assertEquals("Users can only borrow books from their own community", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    /**
     * Same-community borrow must be allowed.
     */
    @Test
    void testCreateBorrowRequest_SameCommunity_Allowed() {
        User borrower = new User("Borrower", "borrower@example.com", "hash", "Community");
        borrower.setCommunityId(5L);

        User owner = new User("Owner", "owner@example.com", "hash", "Community");
        owner.setCommunityId(5L); // SAME community

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(200L)).thenReturn(true);
        when(userRepository.findById(200L)).thenReturn(Optional.of(borrower));
        when(userRepository.findById(100L)).thenReturn(Optional.of(owner));
        when(borrowRequestRepository.existsByBookIdAndBorrowerIdAndStatus(
                1L, 200L, BorrowRequest.STATUS_REQUESTED)).thenReturn(false);
        when(borrowRequestRepository.save(any(BorrowRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BorrowRequest result = borrowRequestService.createBorrowRequest(sampleRequest);
        assertNotNull(result);
        assertEquals(BorrowRequest.STATUS_REQUESTED, result.getStatus());
    }

    /**
     * User without community cannot borrow from a community-restricted owner.
     */
    @Test
    void testCreateBorrowRequest_UserWithoutCommunity_CannotBorrowFromCommunity() {
        User borrower = new User("Borrower", "borrower@example.com", "hash", "NoComm");
        borrower.setCommunityId(null); // No community

        User owner = new User("Owner", "owner@example.com", "hash", "Community");
        owner.setCommunityId(5L); // Community owner

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(200L)).thenReturn(true);
        when(userRepository.findById(200L)).thenReturn(Optional.of(borrower));
        when(userRepository.findById(100L)).thenReturn(Optional.of(owner));

        CommunityAccessException ex = assertThrows(CommunityAccessException.class, () ->
                borrowRequestService.createBorrowRequest(sampleRequest));
        assertEquals("A user must belong to a community to participate in community book sharing", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any(BorrowRequest.class));
    }

    /**
     * Legacy compatibility: when both borrower and owner have null communityId,
     * request is allowed to preserve existing legacy test suites.
     */
    @Test
    void testCreateBorrowRequest_BothNullCommunity_MigrationCompatibility() {
        User borrower = new User("Borrower", "borrower@example.com", "hash", "LegacyBorrower");
        borrower.setCommunityId(null); // legacy borrower

        User owner = new User("Owner", "owner@example.com", "hash", "LegacyOwner");
        owner.setCommunityId(null); // legacy owner

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(200L)).thenReturn(true);
        when(userRepository.findById(200L)).thenReturn(Optional.of(borrower));
        when(userRepository.findById(100L)).thenReturn(Optional.of(owner));
        when(borrowRequestRepository.existsByBookIdAndBorrowerIdAndStatus(
                1L, 200L, BorrowRequest.STATUS_REQUESTED)).thenReturn(false);
        when(borrowRequestRepository.save(any(BorrowRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BorrowRequest result = borrowRequestService.createBorrowRequest(sampleRequest);
        assertNotNull(result);
    }

    // =========================================================================
    // Terminal RETURNED state tests
    // =========================================================================

    // Terminal RETURNED state tests
    @Test
    void testTerminalReturnedStateCannotTransitionFurther() {
        sampleRequest.setStatus(BorrowRequest.STATUS_RETURNED);
        when(borrowRequestRepository.findById(10L)).thenReturn(Optional.of(sampleRequest));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));

        assertThrows(BorrowRequestStateException.class, () -> borrowRequestService.acceptBorrowRequest(10L, 100L));
        assertThrows(BorrowRequestStateException.class, () -> borrowRequestService.rejectBorrowRequest(10L, 100L));
        assertThrows(BorrowRequestStateException.class, () -> borrowRequestService.handoverBorrowRequest(10L, 100L));
        assertThrows(BorrowRequestStateException.class, () -> borrowRequestService.requestReturn(10L, 200L));
        assertThrows(BorrowRequestStateException.class, () -> borrowRequestService.confirmReturn(10L, 100L));
    }
}
