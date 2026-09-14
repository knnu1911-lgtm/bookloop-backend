package bookloop_backend.model;

/**
 * Request body for creating a new community.
 */
public class CreateCommunityRequest {

    private String name;
    private String type;
    private Long createdBy;

    public CreateCommunityRequest() {
    }

    public CreateCommunityRequest(String name, String type, Long createdBy) {
        this.name = name;
        this.type = type;
        this.createdBy = createdBy;
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

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
