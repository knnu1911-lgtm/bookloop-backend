package bookloop_backend.model;

/**
 * Request body for joining a community by code.
 */
public class JoinCommunityRequest {

    private Long userId;
    private String code;

    public JoinCommunityRequest() {
    }

    public JoinCommunityRequest(Long userId, String code) {
        this.userId = userId;
        this.code = code;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
