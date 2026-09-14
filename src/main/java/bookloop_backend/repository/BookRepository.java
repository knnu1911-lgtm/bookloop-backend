package bookloop_backend.repository;

import bookloop_backend.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByOwnerId(Long ownerId);
}
