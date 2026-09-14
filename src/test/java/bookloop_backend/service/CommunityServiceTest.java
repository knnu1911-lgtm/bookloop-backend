package bookloop_backend.service;

import bookloop_backend.exception.CommunityAccessException;
import bookloop_backend.exception.CommunityNotFoundException;
import bookloop_backend.exception.InvalidCommunityException;
import bookloop_backend.model.Community;
import bookloop_backend.model.User;
import bookloop_backend.model.UserPublicInfo;
import bookloop_backend.repository.CommunityRepository;
import bookloop_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommunityService communityService;

    private User userWithoutCommunity;
    private User userWithCommunity;
    private Community communityA;

    @BeforeEach
    void setUp() {
        userWithoutCommunity = new User("Alice", "alice@example.com", "hashed", "Legacy");
        userWithoutCommunity.setCommunityId(null);
        // Simulate a saved user with id=1
        setId(userWithoutCommunity, 1L);

        userWithCommunity = new User("Bob", "bob@example.com", "hashed", "Legacy");
        userWithCommunity.setCommunityId(10L);
        setId(userWithCommunity, 2L);

        communityA = new Community("Green Park Apartments", "RESIDENTIAL", "GRN24X7", 1L, LocalDateTime.now());
        setCommunityId(communityA, 10L);
    }

    // =========================================================================
    // createCommunity tests
    // =========================================================================

    // Test 1: Valid community creation
    @Test
    void testCreateCommunity_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithoutCommunity));
        when(communityRepository.existsByCode(anyString())).thenReturn(false);
        when(communityRepository.save(any(Community.class))).thenAnswer(inv -> {
            Community c = inv.getArgument(0);
            setCommunityId(c, 10L);
            return c;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Community result = communityService.createCommunity("Green Park Apartments", "RESIDENTIAL", 1L);

        assertNotNull(result);
        assertEquals("Green Park Apartments", result.getName());
        assertEquals("RESIDENTIAL", result.getType());
        assertNotNull(result.getCode());
        assertEquals(7, result.getCode().length()); // 3+2+2 = 7 chars
        assertEquals(1L, result.getCreatedBy());
        assertNotNull(result.getCreatedAt());

        // Creator must be enrolled
        assertEquals(10L, userWithoutCommunity.getCommunityId());
        verify(userRepository, times(1)).save(userWithoutCommunity);
    }

    // Test 2: Missing name
    @Test
    void testCreateCommunity_MissingName() {
        InvalidCommunityException ex = assertThrows(InvalidCommunityException.class, () ->
                communityService.createCommunity("", "RESIDENTIAL", 1L));
        assertEquals("Community name is required", ex.getMessage());
        verify(communityRepository, never()).save(any());
    }

    @Test
    void testCreateCommunity_NullName() {
        InvalidCommunityException ex = assertThrows(InvalidCommunityException.class, () ->
                communityService.createCommunity(null, "RESIDENTIAL", 1L));
        assertEquals("Community name is required", ex.getMessage());
    }

    // Test 3: Invalid type
    @Test
    void testCreateCommunity_InvalidType() {
        InvalidCommunityException ex = assertThrows(InvalidCommunityException.class, () ->
                communityService.createCommunity("Green Park", "UNIVERSITY", 1L));
        assertTrue(ex.getMessage().contains("RESIDENTIAL, OFFICE, OTHER"));
        verify(communityRepository, never()).save(any());
    }

    @Test
    void testCreateCommunity_NullType() {
        InvalidCommunityException ex = assertThrows(InvalidCommunityException.class, () ->
                communityService.createCommunity("Green Park", null, 1L));
        assertTrue(ex.getMessage().contains("RESIDENTIAL, OFFICE, OTHER"));
    }

    // Test 4: Non-existent creator
    @Test
    void testCreateCommunity_CreatorNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        CommunityNotFoundException ex = assertThrows(CommunityNotFoundException.class, () ->
                communityService.createCommunity("Green Park", "RESIDENTIAL", 999L));
        assertTrue(ex.getMessage().contains("999"));
        verify(communityRepository, never()).save(any());
    }

    @Test
    void testCreateCommunity_NullCreatorId() {
        InvalidCommunityException ex = assertThrows(InvalidCommunityException.class, () ->
                communityService.createCommunity("Green Park", "RESIDENTIAL", null));
        assertEquals("Creator user ID is required", ex.getMessage());
    }

    // Test 5: Unique code generation — type variants accepted
    @Test
    void testCreateCommunity_TypeVariants() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithoutCommunity));
        when(communityRepository.existsByCode(anyString())).thenReturn(false);
        when(communityRepository.save(any(Community.class))).thenAnswer(inv -> {
            Community c = inv.getArgument(0);
            setCommunityId(c, 20L);
            return c;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Community office = communityService.createCommunity("TechHub", "OFFICE", 1L);
        assertEquals("OFFICE", office.getType());

        // Reset user for second call
        userWithoutCommunity.setCommunityId(null);

        Community other = communityService.createCommunity("Book Club", "OTHER", 1L);
        assertEquals("OTHER", other.getType());
    }

    // Creator auto-becomes member
    @Test
    void testCreateCommunity_CreatorAutomaticallyJoins() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithoutCommunity));
        when(communityRepository.existsByCode(anyString())).thenReturn(false);
        when(communityRepository.save(any(Community.class))).thenAnswer(inv -> {
            Community c = inv.getArgument(0);
            setCommunityId(c, 42L);
            return c;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNull(userWithoutCommunity.getCommunityId());
        communityService.createCommunity("Test Community", "OTHER", 1L);
        assertEquals(42L, userWithoutCommunity.getCommunityId()); // Creator now a member
    }

    // Creator already in a community — rejected
    @Test
    void testCreateCommunity_CreatorAlreadyInCommunity() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithCommunity)); // communityId=10
        CommunityAccessException ex = assertThrows(CommunityAccessException.class, () ->
                communityService.createCommunity("New Place", "RESIDENTIAL", 2L));
        assertTrue(ex.getMessage().contains("10"));
        verify(communityRepository, never()).save(any());
    }

    // =========================================================================
    // joinCommunity tests
    // =========================================================================

    // Test 6: Valid join by code
    @Test
    void testJoinCommunity_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithoutCommunity));
        when(communityRepository.findByCode("GRN24X7")).thenReturn(Optional.of(communityA));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Community joined = communityService.joinCommunity(1L, "GRN24X7");

        assertNotNull(joined);
        assertEquals("GRN24X7", joined.getCode());
        assertEquals(10L, userWithoutCommunity.getCommunityId()); // communityA id=10
        verify(userRepository, times(1)).save(userWithoutCommunity);
    }

    // Test 7: Invalid code
    @Test
    void testJoinCommunity_InvalidCode() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithoutCommunity));
        when(communityRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        CommunityNotFoundException ex = assertThrows(CommunityNotFoundException.class, () ->
                communityService.joinCommunity(1L, "INVALID"));
        assertTrue(ex.getMessage().contains("INVALID"));
        verify(userRepository, never()).save(any());
    }

    // Test 8: Missing user
    @Test
    void testJoinCommunity_UserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        CommunityNotFoundException ex = assertThrows(CommunityNotFoundException.class, () ->
                communityService.joinCommunity(999L, "GRN24X7"));
        assertTrue(ex.getMessage().contains("999"));
    }

    // Test 9: User gets correct communityId
    @Test
    void testJoinCommunity_UserGetsCommunityId() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithoutCommunity));
        when(communityRepository.findByCode("GRN24X7")).thenReturn(Optional.of(communityA));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNull(userWithoutCommunity.getCommunityId());
        communityService.joinCommunity(1L, "GRN24X7");
        assertEquals(communityA.getId(), userWithoutCommunity.getCommunityId());
    }

    // Test 10: User already in same community — idempotent
    @Test
    void testJoinCommunity_AlreadyMemberOfSameCommunity_Idempotent() {
        userWithCommunity.setCommunityId(10L); // already in communityA (id=10)
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithCommunity));
        when(communityRepository.findByCode("GRN24X7")).thenReturn(Optional.of(communityA)); // id=10

        Community result = communityService.joinCommunity(2L, "GRN24X7");

        assertNotNull(result);
        // No re-save needed for idempotent join
        verify(userRepository, never()).save(any());
    }

    // User already in a DIFFERENT community — rejected
    @Test
    void testJoinCommunity_AlreadyInDifferentCommunity_Rejected() {
        // communityA has id=10, user is in community 99
        userWithCommunity.setCommunityId(99L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithCommunity));
        when(communityRepository.findByCode("GRN24X7")).thenReturn(Optional.of(communityA));

        CommunityAccessException ex = assertThrows(CommunityAccessException.class, () ->
                communityService.joinCommunity(2L, "GRN24X7"));
        assertTrue(ex.getMessage().contains("99"));
        verify(userRepository, never()).save(any());
    }

    // =========================================================================
    // getCommunity tests
    // =========================================================================

    // Test 11: Found
    @Test
    void testGetCommunity_Found() {
        when(communityRepository.findById(10L)).thenReturn(Optional.of(communityA));

        Community result = communityService.getCommunity(10L);

        assertNotNull(result);
        assertEquals("GRN24X7", result.getCode());
    }

    // Test 12: Not found
    @Test
    void testGetCommunity_NotFound() {
        when(communityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () ->
                communityService.getCommunity(999L));
    }

    // =========================================================================
    // getMembers tests — passwords must never appear
    // =========================================================================

    // Test 13: Members returned as UserPublicInfo (no password)
    @Test
    void testGetMembers_NeverExposesPasswords() {
        when(communityRepository.existsById(10L)).thenReturn(true);
        when(userRepository.findByCommunityId(10L)).thenReturn(Arrays.asList(userWithCommunity));

        List<UserPublicInfo> members = communityService.getMembers(10L);

        assertNotNull(members);
        assertEquals(1, members.size());
        UserPublicInfo info = members.get(0);
        assertEquals("Bob", info.getName());
        assertEquals("bob@example.com", info.getEmail());
        assertEquals(10L, info.getCommunityId());
        // UserPublicInfo has no getPassword() method — password cannot leak
    }

    // Test 14: Community not found for members
    @Test
    void testGetMembers_CommunityNotFound() {
        when(communityRepository.existsById(999L)).thenReturn(false);

        assertThrows(CommunityNotFoundException.class, () ->
                communityService.getMembers(999L));
    }

    // Test 15: Empty member list
    @Test
    void testGetMembers_EmptyList() {
        when(communityRepository.existsById(10L)).thenReturn(true);
        when(userRepository.findByCommunityId(10L)).thenReturn(List.of());

        List<UserPublicInfo> members = communityService.getMembers(10L);

        assertNotNull(members);
        assertTrue(members.isEmpty());
    }

    // =========================================================================
    // Helpers — reflection to set private id fields set by JPA in production
    // =========================================================================

    private void setId(User user, Long id) {
        try {
            java.lang.reflect.Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setCommunityId(Community community, Long id) {
        try {
            java.lang.reflect.Field f = Community.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(community, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
