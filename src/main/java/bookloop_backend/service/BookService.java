package bookloop_backend.service;

import bookloop_backend.exception.CommunityAccessException;
import bookloop_backend.exception.CommunityNotFoundException;
import bookloop_backend.model.Book;
import bookloop_backend.model.User;
import bookloop_backend.repository.BookRepository;
import bookloop_backend.repository.CommunityRepository;
import bookloop_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;

    public BookService(BookRepository bookRepository,
                       UserRepository userRepository,
                       CommunityRepository communityRepository) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
    }

    public Book addBook(Book book) {
        return bookRepository.save(book);
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Optional<Book> getBookById(Long id) {
        return bookRepository.findById(id);
    }

    public Optional<Book> updateBook(Long id, Book updatedBook) {
        return bookRepository.findById(id).map(existingBook -> {
            existingBook.setTitle(updatedBook.getTitle());
            existingBook.setAuthor(updatedBook.getAuthor());
            existingBook.setIsbn(updatedBook.getIsbn());
            existingBook.setGenre(updatedBook.getGenre());
            existingBook.setEdition(updatedBook.getEdition());
            existingBook.setCondition(updatedBook.getCondition());
            existingBook.setConditionNotes(updatedBook.getConditionNotes());
            existingBook.setAvailability(updatedBook.getAvailability());
            existingBook.setPreferredBorrowingDuration(updatedBook.getPreferredBorrowingDuration());
            if (updatedBook.getOwnerId() != null) {
                existingBook.setOwnerId(updatedBook.getOwnerId());
            }
            return bookRepository.save(existingBook);
        });
    }

    public boolean deleteBook(Long id) {
        if (bookRepository.existsById(id)) {
            bookRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Returns books visible to a requesting user within their community.
     * <p>
     * Security rules enforced on the backend:
     * 1. The requesting user must exist.
     * 2. The requesting user must belong to the specified community
     *    (cannot enumerate other communities by changing the ID).
     * 3. Only books whose owners are members of that same community are returned.
     *
     * @param communityId      the community to scope to
     * @param requestingUserId ID of the user making the request
     * @return community-scoped list of books
     */
    public List<Book> getBooksByCommunity(Long communityId, Long requestingUserId) {
        // Community must exist
        if (!communityRepository.existsById(communityId)) {
            throw new CommunityNotFoundException("Community not found with ID: " + communityId);
        }

        // Requesting user must exist
        User requestingUser = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new CommunityNotFoundException(
                        "Requesting user not found with ID: " + requestingUserId));

        // Requesting user must belong to the requested community
        if (requestingUser.getCommunityId() == null
                || !requestingUser.getCommunityId().equals(communityId)) {
            throw new CommunityAccessException(
                    "Access denied: you do not belong to community ID " + communityId);
        }

        // Collect owner IDs who belong to this community
        List<User> members = userRepository.findByCommunityId(communityId);
        Set<Long> memberIds = members.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        // Filter all books to only those owned by community members
        return bookRepository.findAll().stream()
                .filter(book -> book.getOwnerId() != null && memberIds.contains(book.getOwnerId()))
                .collect(Collectors.toList());
    }
}

