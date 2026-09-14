package bookloop_backend.controller;

import bookloop_backend.model.Book;
import bookloop_backend.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    public ResponseEntity<Book> addBook(@Valid @RequestBody Book book) {
        Book savedBook = bookService.addBook(book);
        return ResponseEntity.ok(savedBook);
    }

    /**
     * GET /api/books
     * <p>
     * Without parameters: returns all books (backward-compatible, used by existing tests).
     * <p>
     * With communityId + requestingUserId: returns only books belonging to members of
     * that community. The backend validates that the requesting user actually belongs
     * to the specified community, preventing cross-community enumeration.
     *
     * @param communityId      (optional) scope results to this community
     * @param requestingUserId (required when communityId is present) ID of the requesting user
     */
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks(
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) Long requestingUserId) {

        if (communityId != null) {
            List<Book> books = bookService.getBooksByCommunity(communityId, requestingUserId);
            return ResponseEntity.ok(books);
        }
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        return bookService.getBookById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable Long id, @Valid @RequestBody Book book) {
        return bookService.updateBook(id, book)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        if (bookService.deleteBook(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

