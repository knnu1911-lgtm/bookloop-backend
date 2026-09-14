package bookloop_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "communities")
public class Community {

    /** Allowed community type values. */
    public static final String TYPE_RESIDENTIAL = "RESIDENTIAL";
    public static final String TYPE_OFFICE = "OFFICE";
    public static final String TYPE_OTHER = "OTHER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name of the community (e.g., "Green Park Apartments"). */
    @Column(nullable = false)
    private String name;

    /** One of RESIDENTIAL, OFFICE, OTHER. */
    @Column(nullable = false)
    private String type;

    /** Human-friendly unique join code (e.g., GRN24X7). */
    @Column(nullable = false, unique = true)
    private String code;

    /** ID of the user who created this community. */
    @Column(name = "created_by")
    private Long createdBy;

    /** Timestamp set automatically at creation. */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Community() {
    }

    public Community(String name, String type, String code, Long createdBy, LocalDateTime createdAt) {
        this.name = name;
        this.type = type;
        this.code = code;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
