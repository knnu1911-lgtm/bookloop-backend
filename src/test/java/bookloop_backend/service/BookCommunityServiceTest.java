package bookloop_backend.service;

import bookloop_backend.exception.CommunityAccessException;
import bookloop_backend.exception.CommunityNotFoundException;
import bookloop_backend.model.Book;
import bookloop_backend.model.User;
import bookloop_backend.repository.BookRepository;
import bookloop_backend.repository.CommunityRepository;
import bookloop_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link BookService#getBooksByCommunity} — community-scoped book discovery.
 *
 * <p>Security rules enforced:
 * <ol>
 *   <li>Community must exist.</li>
 *   <li>Requesting user must exist.</li>
 *   <li>Requesting user must belong to the specified community.</li>
 *   <li>Only books owned by members of that community are returned.</li>
 *   <li>Books from other communities must not be included.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class BookCommunityServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommunityRepository communityRepository;

    @InjectMocks
    private BookService bookService;

    // Community A members
    private User memberA1; // id=1, communityId=10
    private User memberA2; // id=2, communityId=10

    // Community B member
    private User memberB1; // id=3, communityId=20

    // Books
    private Book bookOfA1; // ownerId=1
    private Book bookOfA2; // ownerId=2
    private Book bookOfB1; // ownerId=3

    @BeforeEach
    void setUp() {
        memberA1 = makeUser(1L, "Alice", 10L);
        memberA2 = makeUser(2L, "Charlie", 10L);
        memberB1 = makeUser(3L, "Dave", 20L);

        bookOfA1 = makeBook(1L, "Book A1", 1L);
        bookOfA2 = makeBook(2L, "Book A2", 2L);
        bookOfB1 = makeBook(3L, "Book B1", 3L);
    }

    // -------------------------------------------------------------------------
    // Happy-path: member sees only own-community books
    // -------------------------------------------------------------------------

    @Test
    void testGetBooksByCommunity_MemberSeesOnlyOwnCommunityBooks() {
        when(communityRepository.existsById(10L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(memberA1)); // member of 10
        when(userRepository.findByCommunityId(10L)).thenReturn(Arrays.asList(memberA1, memberA2));
        when(bookRepository.findAll()).thenReturn(Arrays.asList(bookOfA1, bookOfA2, bookOfB1));

        List<Book> result = bookService.getBooksByCommunity(10L, 1L);

        // Should contain A1 and A2 but NOT B1
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(b -> b.getId().equals(1L)));
        assertTrue(result.stream().anyMatch(b -> b.getId().equals(2L)));
        assertFalse(result.stream().anyMatch(b -> b.getId().equals(3L)),
                "Book from community B must not appear in community A results");
    }

    // -------------------------------------------------------------------------
    // Cross-community exclusion: user A cannot access community B's books
    // -------------------------------------------------------------------------

    @Test
    void testGetBooksByCommunity_CrossCommunityAccessRejected() {
        // Alice (communityId=10) tries to query community B (id=20)
        when(communityRepository.existsById(20L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(memberA1)); // communityId=10 != 20

        CommunityAccessException ex = assertThrows(CommunityAccessException.class, () ->
                bookService.getBooksByCommunity(20L, 1L));
        assertTrue(ex.getMessage().contains("20"),
                "Error must mention the community the user tried to access");
    }

    // -------------------------------------------------------------------------
    // User without community cannot perform community-scoped discovery
    // -------------------------------------------------------------------------

    @Test
    void testGetBooksByCommunity_UserWithoutCommunity_Rejected() {
        User noCommunityUser = makeUser(4L, "NoComm", null); // communityId=null
        when(communityRepository.existsById(10L)).thenReturn(true);
        when(userRepository.findById(4L)).thenReturn(Optional.of(noCommunityUser));

        CommunityAccessException ex = assertThrows(CommunityAccessException.class, () ->
                bookService.getBooksByCommunity(10L, 4L));
        assertTrue(ex.getMessage().contains("10"));
    }

    // -------------------------------------------------------------------------
    // Community not found
    // -------------------------------------------------------------------------

    @Test
    void testGetBooksByCommunity_CommunityNotFound() {
        when(communityRepository.existsById(999L)).thenReturn(false);

        assertThrows(CommunityNotFoundException.class, () ->
                bookService.getBooksByCommunity(999L, 1L));
    }

    // -------------------------------------------------------------------------
    // Requesting user not found
    // -------------------------------------------------------------------------

    @Test
    void testGetBooksByCommunity_RequestingUserNotFound() {
        when(communityRepository.existsById(10L)).thenReturn(true);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () ->
                bookService.getBooksByCommunity(10L, 999L));
    }

    // -------------------------------------------------------------------------
    // Only community members' books appear — non-member owner books excluded
    // -------------------------------------------------------------------------

    @Test
    void testGetBooksByCommunity_OnlyMemberBooksReturned_NonMemberExcluded() {
        // Community 10 has only member A1; A2 is not yet in community 10
        User nonMember = makeUser(5L, "NonMember", null);
        Book nonMemberBook = makeBook(10L, "Hidden Book", 5L);

        when(communityRepository.existsById(10L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(memberA1));
        when(userRepository.findByCommunityId(10L)).thenReturn(List.of(memberA1)); // only A1
        when(bookRepository.findAll()).thenReturn(Arrays.asList(bookOfA1, nonMemberBook));

        List<Book> result = bookService.getBooksByCommunity(10L, 1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId()); // only A1's book
        assertFalse(result.stream().anyMatch(b -> b.getId().equals(10L)),
                "Non-member's book must not appear");
    }

    // -------------------------------------------------------------------------
    // Empty community results in empty list
    // -------------------------------------------------------------------------

    @Test
    void testGetBooksByCommunity_EmptyCommunity_ReturnsEmptyList() {
        when(communityRepository.existsById(10L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(memberA1));
        when(userRepository.findByCommunityId(10L)).thenReturn(List.of());
        when(bookRepository.findAll()).thenReturn(List.of());

        List<Book> result = bookService.getBooksByCommunity(10L, 1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User makeUser(Long id, String name, Long communityId) {
        User u = new User(name, name.toLowerCase() + "@example.com", "hash", "Test");
        u.setCommunityId(communityId);
        setId(u, id);
        return u;
    }

    private Book makeBook(Long id, String title, Long ownerId) {
        Book b = new Book(title, "Author", "ISBN", "Genre", "1st",
                "Good", "None", "Available", "14 days", ownerId);
        b.setId(id);
        return b;
    }

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
