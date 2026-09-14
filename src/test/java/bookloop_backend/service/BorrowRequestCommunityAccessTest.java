package bookloop_backend.service;

import bookloop_backend.exception.CommunityAccessException;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for community-based access control in BorrowRequestService.
 * <p>
 * These are separate from BorrowRequestServiceTest to avoid disturbing the
 * existing test suite. The community check is only applied when both the
 * borrower and the book owner have a non-null communityId.
 */
@ExtendWith(MockitoExtension.class)
class BorrowRequestCommunityAccessTest {

    @Mock
    private BorrowRequestRepository borrowRequestRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BorrowRequestService borrowRequestService;

    private Book availableBook;
    private User ownerInCommunityA;
    private User borrowerInCommunityA;
    private User borrowerInCommunityB;
    private User legacyUserNoCommunity;

    @BeforeEach
    void setUp() {
        // Book owned by user 100 (community A = id 1)
        availableBook = new Book("Test Book", "Author", "123", "Fiction", "1st",
                "Good", "None", "Available", "14 days", 100L);
        availableBook.setId(1L);

        // Owner in community A
        ownerInCommunityA = new User("Owner", "owner@test.com", "hashed", "Legacy");
        ownerInCommunityA.setCommunityId(1L);
        setId(ownerInCommunityA, 100L);

        // Borrower in same community A
        borrowerInCommunityA = new User("BorrowerA", "borrowerA@test.com", "hashed", "Legacy");
        borrowerInCommunityA.setCommunityId(1L);
        setId(borrowerInCommunityA, 200L);

        // Borrower in a different community B
        borrowerInCommunityB = new User("BorrowerB", "borrowerB@test.com", "hashed", "Legacy");
        borrowerInCommunityB.setCommunityId(2L);
        setId(borrowerInCommunityB, 300L);

        // Legacy user with null communityId (migration compatibility)
        legacyUserNoCommunity = new User("Legacy", "legacy@test.com", "hashed", "Legacy Org");
        legacyUserNoCommunity.setCommunityId(null);
        setId(legacyUserNoCommunity, 400L);
    }

    // Test 11: Same-community borrow request — ALLOWED
    @Test
    void testCreateBorrowRequest_SameCommunity_Allowed() {
        BorrowRequest request = new BorrowRequest(1L, 200L, "14 days", "Please lend!");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(200L)).thenReturn(true);
        when(userRepository.findById(200L)).thenReturn(Optional.of(borrowerInCommunityA));
        when(userRepository.findById(100L)).thenReturn(Optional.of(ownerInCommunityA));
        when(borrowRequestRepository.existsByBookIdAndBorrowerIdAndStatus(1L, 200L, BorrowRequest.STATUS_REQUESTED)).thenReturn(false);
        when(borrowRequestRepository.save(any(BorrowRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowRequest result = borrowRequestService.createBorrowRequest(request);

        assertNotNull(result);
        assertEquals(BorrowRequest.STATUS_REQUESTED, result.getStatus());
        verify(borrowRequestRepository, times(1)).save(request);
    }

    // Test 12: Cross-community borrow request — REJECTED with 403
    @Test
    void testCreateBorrowRequest_CrossCommunity_Rejected() {
        BorrowRequest request = new BorrowRequest(1L, 300L, "14 days", "Please lend!");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(300L)).thenReturn(true);
        when(userRepository.findById(300L)).thenReturn(Optional.of(borrowerInCommunityB)); // community 2
        when(userRepository.findById(100L)).thenReturn(Optional.of(ownerInCommunityA));   // community 1

        CommunityAccessException ex = assertThrows(CommunityAccessException.class, () ->
                borrowRequestService.createBorrowRequest(request));

        assertEquals("Users can only borrow books from their own community", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any());
    }

    // Test 13: Borrower without community cannot perform new community-restricted borrowing — REJECTED
    @Test
    void testCreateBorrowRequest_BorrowerNullCommunity_Rejected() {
        BorrowRequest request = new BorrowRequest(1L, 400L, "14 days", "Request from user without community");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(400L)).thenReturn(true);
        when(userRepository.findById(400L)).thenReturn(Optional.of(legacyUserNoCommunity)); // communityId=null
        when(userRepository.findById(100L)).thenReturn(Optional.of(ownerInCommunityA));     // communityId=1

        CommunityAccessException ex = assertThrows(CommunityAccessException.class, () ->
                borrowRequestService.createBorrowRequest(request));

        assertEquals("A user must belong to a community to participate in community book sharing", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any());
    }

    // Test 14: Community borrower attempting to borrow book from owner with null community — REJECTED
    @Test
    void testCreateBorrowRequest_OwnerNullCommunity_Rejected() {
        availableBook.setOwnerId(400L);
        BorrowRequest request = new BorrowRequest(1L, 200L, "14 days", "Request to owner without community");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(200L)).thenReturn(true);
        when(userRepository.findById(200L)).thenReturn(Optional.of(borrowerInCommunityA));  // communityId=1
        when(userRepository.findById(400L)).thenReturn(Optional.of(legacyUserNoCommunity)); // communityId=null

        CommunityAccessException ex = assertThrows(CommunityAccessException.class, () ->
                borrowRequestService.createBorrowRequest(request));

        assertEquals("Cannot borrow a book that does not belong to an active community", ex.getMessage());
        verify(borrowRequestRepository, never()).save(any());
    }

    // Test 15: Both parties have null communityId — MIGRATION COMPAT
    @Test
    void testCreateBorrowRequest_BothNullCommunity_LegacyCompat_Allowed() {
        availableBook.setOwnerId(400L);
        User anotherLegacy = new User("LegacyB", "legacyb@test.com", "hashed", "Old Org");
        anotherLegacy.setCommunityId(null);
        setId(anotherLegacy, 500L);

        BorrowRequest request = new BorrowRequest(1L, 500L, "14 days", "Legacy to legacy");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook));
        when(userRepository.existsById(500L)).thenReturn(true);
        when(userRepository.findById(500L)).thenReturn(Optional.of(anotherLegacy));
        when(userRepository.findById(400L)).thenReturn(Optional.of(legacyUserNoCommunity));
        when(borrowRequestRepository.existsByBookIdAndBorrowerIdAndStatus(1L, 500L, BorrowRequest.STATUS_REQUESTED)).thenReturn(false);
        when(borrowRequestRepository.save(any(BorrowRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowRequest result = borrowRequestService.createBorrowRequest(request);
        assertNotNull(result);
        verify(borrowRequestRepository, times(1)).save(request);
    }

    // Reflection helper to set private id on User (simulates JPA-generated ID)
    private void setId(User user, Long id) {
        try {
            java.lang.reflect.Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
