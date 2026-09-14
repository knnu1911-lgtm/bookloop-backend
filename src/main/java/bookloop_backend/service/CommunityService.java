package bookloop_backend.service;

import bookloop_backend.exception.CommunityAccessException;
import bookloop_backend.exception.CommunityNotFoundException;
import bookloop_backend.exception.InvalidCommunityException;
import bookloop_backend.model.Community;
import bookloop_backend.model.User;
import bookloop_backend.model.UserPublicInfo;
import bookloop_backend.repository.CommunityRepository;
import bookloop_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class CommunityService {

    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            Community.TYPE_RESIDENTIAL,
            Community.TYPE_OFFICE,
            Community.TYPE_OTHER
    );

    private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I/O to avoid confusion
    private static final String CODE_DIGITS = "0123456789";
    private static final String CODE_ALNUM = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";

    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;
    private final Random random = new Random();

    public CommunityService(CommunityRepository communityRepository,
                            UserRepository userRepository) {
        this.communityRepository = communityRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // A. createCommunity
    // -------------------------------------------------------------------------

    /**
     * Creates a new community.
     * <p>
     * Rules:
     * - name is required
     * - type must be RESIDENTIAL, OFFICE, or OTHER
     * - createdBy must reference an existing user
     * - a unique code is generated automatically (format: 3 letters + 2 digits + 2 alnum = e.g. GRN24X7)
     * - the creator is automatically enrolled as a member (communityId is set)
     * - a creator who already belongs to another community will receive a conflict error
     *
     * @param name      display name of the community
     * @param type      community type (RESIDENTIAL / OFFICE / OTHER)
     * @param creatorId ID of the user creating the community
     * @return the persisted Community
     */
    public Community createCommunity(String name, String type, Long creatorId) {
        if (name == null || name.isBlank()) {
            throw new InvalidCommunityException("Community name is required");
        }
        if (type == null || !ALLOWED_TYPES.contains(type.toUpperCase())) {
            throw new InvalidCommunityException(
                    "Community type must be one of: RESIDENTIAL, OFFICE, OTHER");
        }
        if (creatorId == null) {
            throw new InvalidCommunityException("Creator user ID is required");
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new CommunityNotFoundException(
                        "Creator user not found with ID: " + creatorId));

        // A user already in a community cannot be silently moved to a new one.
        if (creator.getCommunityId() != null) {
            throw new CommunityAccessException(
                    "User is already a member of community ID " + creator.getCommunityId()
                    + ". Leave the current community before creating a new one.");
        }

        String code = generateUniqueCode();

        Community community = new Community(
                name,
                type.toUpperCase(),
                code,
                creatorId,
                LocalDateTime.now()
        );
        Community saved = communityRepository.save(community);

        // Automatically enroll creator
        creator.setCommunityId(saved.getId());
        userRepository.save(creator);

        return saved;
    }

    // -------------------------------------------------------------------------
    // B. joinCommunity
    // -------------------------------------------------------------------------

    /**
     * Joins an existing community using its join code.
     * <p>
     * Rules:
     * - userId must reference an existing user
     * - code must match an existing community
     * - user cannot silently switch from one community to another
     *
     * @param userId user wishing to join
     * @param code   community join code
     * @return the Community the user joined
     */
    public Community joinCommunity(Long userId, String code) {
        if (userId == null) {
            throw new InvalidCommunityException("User ID is required");
        }
        if (code == null || code.isBlank()) {
            throw new InvalidCommunityException("Community code is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityNotFoundException(
                        "User not found with ID: " + userId));

        Community community = communityRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new CommunityNotFoundException(
                        "No community found with code: " + code));

        // Prevent silent community switching
        if (user.getCommunityId() != null) {
            if (user.getCommunityId().equals(community.getId())) {
                // Already a member of this exact community — idempotent / safe
                return community;
            }
            throw new CommunityAccessException(
                    "User is already a member of community ID " + user.getCommunityId()
                    + ". Leave the current community before joining another.");
        }

        user.setCommunityId(community.getId());
        userRepository.save(user);

        return community;
    }

    // -------------------------------------------------------------------------
    // C. getCommunity
    // -------------------------------------------------------------------------

    public Community getCommunity(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(
                        "Community not found with ID: " + communityId));
    }

    // -------------------------------------------------------------------------
    // D. getMembers — never returns passwords
    // -------------------------------------------------------------------------

    public List<UserPublicInfo> getMembers(Long communityId) {
        // Ensure community exists first
        if (!communityRepository.existsById(communityId)) {
            throw new CommunityNotFoundException(
                    "Community not found with ID: " + communityId);
        }

        List<User> members = userRepository.findByCommunityId(communityId);
        return members.stream()
                .map(u -> new UserPublicInfo(u.getId(), u.getName(), u.getEmail(), u.getCommunityId()))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Code generation
    // -------------------------------------------------------------------------

    /**
     * Generates a unique community code in the format:
     * 3 uppercase letters + 2 digits + 2 alphanumeric characters = 7 chars total.
     * Example: GRN24X7
     * Retries up to 10 times to ensure uniqueness.
     */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = buildCode();
            if (!communityRepository.existsByCode(code)) {
                return code;
            }
        }
        // Fallback with longer suffix — virtually guaranteed unique
        return buildCode() + System.currentTimeMillis() % 1000;
    }

    private String buildCode() {
        StringBuilder sb = new StringBuilder(7);
        // 3 letters
        for (int i = 0; i < 3; i++) {
            sb.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
        }
        // 2 digits
        for (int i = 0; i < 2; i++) {
            sb.append(CODE_DIGITS.charAt(random.nextInt(CODE_DIGITS.length())));
        }
        // 2 alphanumeric
        for (int i = 0; i < 2; i++) {
            sb.append(CODE_ALNUM.charAt(random.nextInt(CODE_ALNUM.length())));
        }
        return sb.toString();
    }
}
