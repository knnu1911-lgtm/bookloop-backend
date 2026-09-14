package bookloop_backend.controller;

import bookloop_backend.config.GlobalExceptionHandler;
import bookloop_backend.exception.CommunityAccessException;
import bookloop_backend.exception.CommunityNotFoundException;
import bookloop_backend.exception.InvalidCommunityException;
import bookloop_backend.model.Community;
import bookloop_backend.model.CreateCommunityRequest;
import bookloop_backend.model.JoinCommunityRequest;
import bookloop_backend.model.UserPublicInfo;
import bookloop_backend.service.CommunityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CommunityControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CommunityService communityService;

    @InjectMocks
    private CommunityController communityController;

    private Community communityA;
    private Community communityB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(communityController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        communityA = new Community("Green Park Apartments", "RESIDENTIAL", "GRN24X7", 1L, LocalDateTime.now());
        communityB = new Community("TechHub Office", "OFFICE", "TCH85B3", 2L, LocalDateTime.now());
    }

    // =========================================================================
    // POST /api/communities
    // =========================================================================

    @Test
    void testCreateCommunity_Success() throws Exception {
        when(communityService.createCommunity("Green Park Apartments", "RESIDENTIAL", 1L))
                .thenReturn(communityA);

        String requestJson = """
                {
                    "name": "Green Park Apartments",
                    "type": "RESIDENTIAL",
                    "createdBy": 1
                }
                """;

        mockMvc.perform(post("/api/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Green Park Apartments"))
                .andExpect(jsonPath("$.type").value("RESIDENTIAL"))
                .andExpect(jsonPath("$.code").value("GRN24X7"))
                .andExpect(jsonPath("$.createdBy").value(1));

        verify(communityService, times(1)).createCommunity("Green Park Apartments", "RESIDENTIAL", 1L);
    }

    @Test
    void testCreateCommunity_InvalidType() throws Exception {
        when(communityService.createCommunity(anyString(), eq("UNIVERSITY"), anyLong()))
                .thenThrow(new InvalidCommunityException("Community type must be one of: RESIDENTIAL, OFFICE, OTHER"));

        String requestJson = """
                {
                    "name": "ABC Uni",
                    "type": "UNIVERSITY",
                    "createdBy": 1
                }
                """;

        mockMvc.perform(post("/api/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Community type must be one of: RESIDENTIAL, OFFICE, OTHER"));
    }

    @Test
    void testCreateCommunity_CreatorNotFound() throws Exception {
        when(communityService.createCommunity(anyString(), anyString(), eq(999L)))
                .thenThrow(new CommunityNotFoundException("Creator user not found with ID: 999"));

        String requestJson = """
                {
                    "name": "Green Park",
                    "type": "RESIDENTIAL",
                    "createdBy": 999
                }
                """;

        mockMvc.perform(post("/api/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Creator user not found with ID: 999"));
    }

    @Test
    void testCreateCommunity_CreatorAlreadyInCommunity() throws Exception {
        when(communityService.createCommunity(anyString(), anyString(), eq(2L)))
                .thenThrow(new CommunityAccessException("User is already a member of community ID 10. Leave the current community before creating a new one."));

        String requestJson = """
                {
                    "name": "New Place",
                    "type": "OTHER",
                    "createdBy": 2
                }
                """;

        mockMvc.perform(post("/api/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("already a member")));
    }

    // =========================================================================
    // POST /api/communities/join
    // =========================================================================

    @Test
    void testJoinCommunity_Success() throws Exception {
        when(communityService.joinCommunity(3L, "GRN24X7")).thenReturn(communityA);

        String requestJson = """
                {
                    "userId": 3,
                    "code": "GRN24X7"
                }
                """;

        mockMvc.perform(post("/api/communities/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GRN24X7"))
                .andExpect(jsonPath("$.name").value("Green Park Apartments"));

        verify(communityService, times(1)).joinCommunity(3L, "GRN24X7");
    }

    @Test
    void testJoinCommunity_InvalidCode() throws Exception {
        when(communityService.joinCommunity(3L, "BADCODE"))
                .thenThrow(new CommunityNotFoundException("No community found with code: BADCODE"));

        String requestJson = """
                {
                    "userId": 3,
                    "code": "BADCODE"
                }
                """;

        mockMvc.perform(post("/api/communities/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No community found with code: BADCODE"));
    }

    @Test
    void testJoinCommunity_UserAlreadyInDifferentCommunity() throws Exception {
        when(communityService.joinCommunity(2L, "GRN24X7"))
                .thenThrow(new CommunityAccessException("User is already a member of community ID 99. Leave the current community before joining another."));

        String requestJson = """
                {
                    "userId": 2,
                    "code": "GRN24X7"
                }
                """;

        mockMvc.perform(post("/api/communities/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("already a member")));
    }

    @Test
    void testJoinCommunity_UserNotFound() throws Exception {
        when(communityService.joinCommunity(999L, "GRN24X7"))
                .thenThrow(new CommunityNotFoundException("User not found with ID: 999"));

        String requestJson = """
                {
                    "userId": 999,
                    "code": "GRN24X7"
                }
                """;

        mockMvc.perform(post("/api/communities/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with ID: 999"));
    }

    // =========================================================================
    // GET /api/communities/{id}
    // =========================================================================

    @Test
    void testGetCommunity_Found() throws Exception {
        when(communityService.getCommunity(1L)).thenReturn(communityA);

        mockMvc.perform(get("/api/communities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Green Park Apartments"))
                .andExpect(jsonPath("$.type").value("RESIDENTIAL"))
                .andExpect(jsonPath("$.code").value("GRN24X7"));

        verify(communityService, times(1)).getCommunity(1L);
    }

    @Test
    void testGetCommunity_NotFound() throws Exception {
        when(communityService.getCommunity(999L))
                .thenThrow(new CommunityNotFoundException("Community not found with ID: 999"));

        mockMvc.perform(get("/api/communities/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Community not found with ID: 999"));
    }

    // =========================================================================
    // GET /api/communities/{id}/members
    // =========================================================================

    @Test
    void testGetMembers_Success_NeverExposesPasswords() throws Exception {
        UserPublicInfo member1 = new UserPublicInfo(1L, "Alice", "alice@example.com", 1L);
        UserPublicInfo member2 = new UserPublicInfo(2L, "Bob", "bob@example.com", 1L);
        when(communityService.getMembers(1L)).thenReturn(Arrays.asList(member1, member2));

        mockMvc.perform(get("/api/communities/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$[0].communityId").value(1))
                // password must never appear in the response
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("Bob"))
                .andExpect(jsonPath("$[1].password").doesNotExist());

        verify(communityService, times(1)).getMembers(1L);
    }

    @Test
    void testGetMembers_CommunityNotFound() throws Exception {
        when(communityService.getMembers(999L))
                .thenThrow(new CommunityNotFoundException("Community not found with ID: 999"));

        mockMvc.perform(get("/api/communities/999/members"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Community not found with ID: 999"));
    }

    @Test
    void testGetMembers_EmptyList() throws Exception {
        when(communityService.getMembers(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/communities/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // Helper for anyString eq combination
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
