package bookloop_backend.controller;

import bookloop_backend.config.GlobalExceptionHandler;
import bookloop_backend.model.Book;
import bookloop_backend.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BookControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BookService bookService;

    @InjectMocks
    private BookController bookController;

    private Book validBook;

    private final String validBookJson = """
            {
                "title": "Clean Architecture",
                "author": "Robert C. Martin",
                "isbn": "9780134494166",
                "genre": "Software Engineering",
                "edition": "1st",
                "condition": "Like New",
                "conditionNotes": "Crisp pages",
                "availability": "Available",
                "preferredBorrowingDuration": "14 days",
                "ownerId": 42
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(bookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        validBook = new Book(
                "Clean Architecture",
                "Robert C. Martin",
                "9780134494166",
                "Software Engineering",
                "1st",
                "Like New",
                "Crisp pages",
                "Available",
                "14 days",
                42L
        );
        validBook.setId(10L);
    }

    @Test
    void testAddBook_Success() throws Exception {
        when(bookService.addBook(any(Book.class))).thenReturn(validBook);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBookJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Clean Architecture"))
                .andExpect(jsonPath("$.author").value("Robert C. Martin"))
                .andExpect(jsonPath("$.isbn").value("9780134494166"))
                .andExpect(jsonPath("$.genre").value("Software Engineering"))
                .andExpect(jsonPath("$.edition").value("1st"))
                .andExpect(jsonPath("$.condition").value("Like New"))
                .andExpect(jsonPath("$.conditionNotes").value("Crisp pages"))
                .andExpect(jsonPath("$.availability").value("Available"))
                .andExpect(jsonPath("$.preferredBorrowingDuration").value("14 days"))
                .andExpect(jsonPath("$.ownerId").value(42));

        verify(bookService, times(1)).addBook(any(Book.class));
    }

    @Test
    void testAddBook_ValidationError_MissingRequiredFields() throws Exception {
        String invalidBookJson = "{}";

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBookJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.title").value("Title is required"))
                .andExpect(jsonPath("$.details.author").value("Author is required"))
                .andExpect(jsonPath("$.details.genre").value("Genre is required"))
                .andExpect(jsonPath("$.details.condition").value("Condition is required"))
                .andExpect(jsonPath("$.details.availability").value("Availability is required"))
                .andExpect(jsonPath("$.details.preferredBorrowingDuration").value("Preferred borrowing duration is required"))
                .andExpect(jsonPath("$.details.ownerId").value("Owner ID is required"));

        verify(bookService, never()).addBook(any(Book.class));
    }

    @Test
    void testGetAllBooks() throws Exception {
        when(bookService.getAllBooks()).thenReturn(Arrays.asList(validBook));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].title").value("Clean Architecture"));

        verify(bookService, times(1)).getAllBooks();
    }

    @Test
    void testGetBookById_Found() throws Exception {
        when(bookService.getBookById(10L)).thenReturn(Optional.of(validBook));

        mockMvc.perform(get("/api/books/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Clean Architecture"));

        verify(bookService, times(1)).getBookById(10L);
    }

    @Test
    void testGetBookById_NotFound() throws Exception {
        when(bookService.getBookById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/books/999"))
                .andExpect(status().isNotFound());

        verify(bookService, times(1)).getBookById(999L);
    }

    @Test
    void testUpdateBook_Success() throws Exception {
        when(bookService.updateBook(eq(10L), any(Book.class))).thenReturn(Optional.of(validBook));

        mockMvc.perform(put("/api/books/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBookJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Clean Architecture"));

        verify(bookService, times(1)).updateBook(eq(10L), any(Book.class));
    }

    @Test
    void testUpdateBook_NotFound() throws Exception {
        when(bookService.updateBook(eq(999L), any(Book.class))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/books/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBookJson))
                .andExpect(status().isNotFound());

        verify(bookService, times(1)).updateBook(eq(999L), any(Book.class));
    }

    @Test
    void testUpdateBook_ValidationError() throws Exception {
        String invalidBookJson = "{}";

        mockMvc.perform(put("/api/books/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBookJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));

        verify(bookService, never()).updateBook(anyLong(), any(Book.class));
    }

    @Test
    void testDeleteBook_Success() throws Exception {
        when(bookService.deleteBook(10L)).thenReturn(true);

        mockMvc.perform(delete("/api/books/10"))
                .andExpect(status().isNoContent());

        verify(bookService, times(1)).deleteBook(10L);
    }

    @Test
    void testDeleteBook_NotFound() throws Exception {
        when(bookService.deleteBook(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/books/999"))
                .andExpect(status().isNotFound());

        verify(bookService, times(1)).deleteBook(999L);
    }
}
