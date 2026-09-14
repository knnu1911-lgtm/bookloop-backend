package bookloop_backend.model;

/**
 * DTO for exposing user information publicly — never contains password or hash.
 */
public class UserPublicInfo {

    private Long id;
    private String name;
    private String email;
    private Long communityId;

    public UserPublicInfo() {
    }

    public UserPublicInfo(Long id, String name, String email, Long communityId) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.communityId = communityId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Long getCommunityId() {
        return communityId;
    }
}
