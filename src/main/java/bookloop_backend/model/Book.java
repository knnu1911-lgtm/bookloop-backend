package bookloop_backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Author is required")
    private String author;

    private String isbn;

    @NotBlank(message = "Genre is required")
    private String genre;

    private String edition;

    @NotBlank(message = "Condition is required")
    private String condition;

    private String conditionNotes;

    @NotBlank(message = "Availability is required")
    private String availability;

    @NotBlank(message = "Preferred borrowing duration is required")
    private String preferredBorrowingDuration;

    @NotNull(message = "Owner ID is required")
    private Long ownerId;

    public Book() {
    }

    public Book(String title, String author, String isbn, String genre, String edition,
                String condition, String conditionNotes, String availability,
                String preferredBorrowingDuration, Long ownerId) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.genre = genre;
        this.edition = edition;
        this.condition = condition;
        this.conditionNotes = conditionNotes;
        this.availability = availability;
        this.preferredBorrowingDuration = preferredBorrowingDuration;
        this.ownerId = ownerId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getConditionNotes() {
        return conditionNotes;
    }

    public void setConditionNotes(String conditionNotes) {
        this.conditionNotes = conditionNotes;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public String getPreferredBorrowingDuration() {
        return preferredBorrowingDuration;
    }

    public void setPreferredBorrowingDuration(String preferredBorrowingDuration) {
        this.preferredBorrowingDuration = preferredBorrowingDuration;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
}
