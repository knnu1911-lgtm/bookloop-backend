package bookloop_backend.controller;

import bookloop_backend.model.Community;
import bookloop_backend.model.CreateCommunityRequest;
import bookloop_backend.model.JoinCommunityRequest;
import bookloop_backend.model.UserPublicInfo;
import bookloop_backend.service.CommunityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/communities")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /**
     * POST /api/communities
     * <p>
     * Creates a new community. The creator is automatically enrolled as a member.
     *
     * Example request:
     * {
     *   "name": "Green Park Apartments",
     *   "type": "RESIDENTIAL",
     *   "createdBy": 17
     * }
     */
    @PostMapping
    public ResponseEntity<Community> createCommunity(@RequestBody CreateCommunityRequest request) {
        Community community = communityService.createCommunity(
                request.getName(),
                request.getType(),
                request.getCreatedBy()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(community);
    }

    /**
     * POST /api/communities/join
     * <p>
     * Join a community using its unique code.
     *
     * Example request:
     * {
     *   "userId": 16,
     *   "code": "GRN24X7"
     * }
     */
    @PostMapping("/join")
    public ResponseEntity<Community> joinCommunity(@RequestBody JoinCommunityRequest request) {
        Community community = communityService.joinCommunity(request.getUserId(), request.getCode());
        return ResponseEntity.ok(community);
    }

    /**
     * GET /api/communities/{id}
     * <p>
     * Returns community details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Community> getCommunity(@PathVariable Long id) {
        Community community = communityService.getCommunity(id);
        return ResponseEntity.ok(community);
    }

    /**
     * GET /api/communities/{id}/members
     * <p>
     * Returns the list of community members. Passwords are never included.
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<UserPublicInfo>> getMembers(@PathVariable Long id) {
        List<UserPublicInfo> members = communityService.getMembers(id);
        return ResponseEntity.ok(members);
    }
}
