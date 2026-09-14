package bookloop_backend.service;

import bookloop_backend.model.Book;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommunityRepository communityRepository;

    @InjectMocks
    private BookService bookService;

    private Book sampleBook;

    @BeforeEach
    void setUp() {
        sampleBook = new Book(
                "The Great Gatsby",
                "F. Scott Fitzgerald",
                "9780743273565",
                "Classic",
                "First",
                "Good",
                "Slight wear on spine",
                "Available",
                "14 days",
                1L
        );
        sampleBook.setId(1L);
    }

    @Test
    void testAddBook() {
        when(bookRepository.save(any(Book.class))).thenReturn(sampleBook);

        Book created = bookService.addBook(sampleBook);

        assertNotNull(created);
        assertEquals("The Great Gatsby", created.getTitle());
        assertEquals("Available", created.getAvailability());
        verify(bookRepository, times(1)).save(sampleBook);
    }

    @Test
    void testGetAllBooks() {
        when(bookRepository.findAll()).thenReturn(Arrays.asList(sampleBook));

        List<Book> books = bookService.getAllBooks();

        assertEquals(1, books.size());
        assertEquals("The Great Gatsby", books.get(0).getTitle());
        verify(bookRepository, times(1)).findAll();
    }

    @Test
    void testGetBookById_Found() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));

        Optional<Book> found = bookService.getBookById(1L);

        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getId());
        verify(bookRepository, times(1)).findById(1L);
    }

    @Test
    void testGetBookById_NotFound() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Book> found = bookService.getBookById(99L);

        assertFalse(found.isPresent());
        verify(bookRepository, times(1)).findById(99L);
    }

    @Test
    void testUpdateBook_Success() {
        Book updatedInfo = new Book(
                "The Great Gatsby - Updated",
                "F. Scott Fitzgerald",
                "9780743273565",
                "Classic",
                "Second",
                "Like New",
                "No marks",
                "Borrowed",
                "21 days",
                2L
        );

        when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Book> result = bookService.updateBook(1L, updatedInfo);

        assertTrue(result.isPresent());
        assertEquals("The Great Gatsby - Updated", result.get().getTitle());
        assertEquals("Like New", result.get().getCondition());
        assertEquals("Borrowed", result.get().getAvailability());
        assertEquals("21 days", result.get().getPreferredBorrowingDuration());
        assertEquals(2L, result.get().getOwnerId());
        verify(bookRepository, times(1)).save(any(Book.class));
    }

    @Test
    void testUpdateBook_NotFound() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Book> result = bookService.updateBook(99L, sampleBook);

        assertFalse(result.isPresent());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void testDeleteBook_Success() {
        when(bookRepository.existsById(1L)).thenReturn(true);
        doNothing().when(bookRepository).deleteById(1L);

        boolean deleted = bookService.deleteBook(1L);

        assertTrue(deleted);
        verify(bookRepository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteBook_NotFound() {
        when(bookRepository.existsById(99L)).thenReturn(false);

        boolean deleted = bookService.deleteBook(99L);

        assertFalse(deleted);
        verify(bookRepository, never()).deleteById(99L);
    }
}
