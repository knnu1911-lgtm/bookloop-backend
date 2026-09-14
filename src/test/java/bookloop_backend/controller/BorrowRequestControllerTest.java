package bookloop_backend.controller;

import bookloop_backend.config.GlobalExceptionHandler;
import bookloop_backend.exception.*;
import bookloop_backend.model.BorrowRequest;
import bookloop_backend.service.BorrowRequestService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BorrowRequestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BorrowRequestService borrowRequestService;

    @InjectMocks
    private BorrowRequestController borrowRequestController;

    private BorrowRequest validRequest;

    private final String validRequestJson = """
            {
                "bookId": 1,
                "borrowerId": 200,
                "requestedDuration": "14 days",
                "message": "Interested in reading this"
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(borrowRequestController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        validRequest = new BorrowRequest(
                1L,
                1L,
                200L,
                "14 days",
                "Interested in reading this",
                BorrowRequest.STATUS_REQUESTED,
                LocalDateTime.now()
        );
    }

    @Test
    void testCreateBorrowRequest_Success() throws Exception {
        when(borrowRequestService.createBorrowRequest(any(BorrowRequest.class))).thenReturn(validRequest);

        mockMvc.perform(post("/api/borrow-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.bookId").value(1))
                .andExpect(jsonPath("$.borrowerId").value(200))
                .andExpect(jsonPath("$.requestedDuration").value("14 days"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        verify(borrowRequestService, times(1)).createBorrowRequest(any(BorrowRequest.class));
    }

    @Test
    void testCreateBorrowRequest_ValidationError_MissingFields() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post("/api/borrow-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.bookId").value("Book ID is required"))
                .andExpect(jsonPath("$.details.borrowerId").value("Borrower ID is required"))
                .andExpect(jsonPath("$.details.requestedDuration").value("Requested duration is required"));

        verify(borrowRequestService, never()).createBorrowRequest(any(BorrowRequest.class));
    }

    @Test
    void testCreateBorrowRequest_OwnBook() throws Exception {
        when(borrowRequestService.createBorrowRequest(any(BorrowRequest.class)))
                .thenThrow(new InvalidBorrowRequestException("User cannot request to borrow their own book"));

        mockMvc.perform(post("/api/borrow-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User cannot request to borrow their own book"));
    }

    @Test
    void testCreateBorrowRequest_DuplicatePending() throws Exception {
        when(borrowRequestService.createBorrowRequest(any(BorrowRequest.class)))
                .thenThrow(new DuplicateBorrowRequestException("A pending borrow request already exists for this book"));

        mockMvc.perform(post("/api/borrow-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A pending borrow request already exists for this book"));
    }

    @Test
    void testCreateBorrowRequest_UnavailableBook() throws Exception {
        when(borrowRequestService.createBorrowRequest(any(BorrowRequest.class)))
                .thenThrow(new BookNotAvailableException("Book is not available for borrowing"));

        mockMvc.perform(post("/api/borrow-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Book is not available for borrowing"));
    }

    @Test
    void testCreateBorrowRequest_NotFound() throws Exception {
        when(borrowRequestService.createBorrowRequest(any(BorrowRequest.class)))
                .thenThrow(new ResourceNotFoundException("Book not found"));

        mockMvc.perform(post("/api/borrow-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Book not found"));
    }

    @Test
    void testGetBorrowRequests_WithUserId() throws Exception {
        when(borrowRequestService.getBorrowRequests(100L)).thenReturn(Arrays.asList(validRequest));

        mockMvc.perform(get("/api/borrow-requests?userId=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].bookId").value(1));

        verify(borrowRequestService, times(1)).getBorrowRequests(100L);
    }

    @Test
    void testAcceptBorrowRequest_Success() throws Exception {
        BorrowRequest accepted = new BorrowRequest(
                1L, 1L, 200L, "14 days", "Interested", BorrowRequest.STATUS_ACCEPTED, LocalDateTime.now()
        );
        when(borrowRequestService.acceptBorrowRequest(eq(1L), eq(100L))).thenReturn(accepted);

        mockMvc.perform(put("/api/borrow-requests/1/accept?ownerId=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(borrowRequestService, times(1)).acceptBorrowRequest(1L, 100L);
    }

    @Test
    void testAcceptBorrowRequest_NonOwner_Forbidden() throws Exception {
        when(borrowRequestService.acceptBorrowRequest(eq(1L), eq(999L)))
                .thenThrow(new UnauthorizedActionException("Only the book owner is authorized to accept this request"));

        mockMvc.perform(put("/api/borrow-requests/1/accept?ownerId=999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the book owner is authorized to accept this request"));
    }

    @Test
    void testAcceptBorrowRequest_AlreadyProcessed_Conflict() throws Exception {
        when(borrowRequestService.acceptBorrowRequest(eq(1L), eq(100L)))
                .thenThrow(new BorrowRequestStateException("Only requests in REQUESTED status can be accepted"));

        mockMvc.perform(put("/api/borrow-requests/1/accept?ownerId=100"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only requests in REQUESTED status can be accepted"));
    }

    @Test
    void testRejectBorrowRequest_Success() throws Exception {
        BorrowRequest rejected = new BorrowRequest(
                1L, 1L, 200L, "14 days", "Interested", BorrowRequest.STATUS_REJECTED, LocalDateTime.now()
        );
        when(borrowRequestService.rejectBorrowRequest(eq(1L), eq(100L))).thenReturn(rejected);

        mockMvc.perform(put("/api/borrow-requests/1/reject?ownerId=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(borrowRequestService, times(1)).rejectBorrowRequest(1L, 100L);
    }

    @Test
    void testRejectBorrowRequest_NonOwner_Forbidden() throws Exception {
        when(borrowRequestService.rejectBorrowRequest(eq(1L), eq(999L)))
                .thenThrow(new UnauthorizedActionException("Only the book owner is authorized to reject this request"));

        mockMvc.perform(put("/api/borrow-requests/1/reject?ownerId=999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the book owner is authorized to reject this request"));
    }

    // Handover Controller Tests
    @Test
    void testHandoverBorrowRequest_Success() throws Exception {
        BorrowRequest handedOver = new BorrowRequest(
                1L, 1L, 200L, "14 days", "Interested", BorrowRequest.STATUS_HANDED_OVER,
                LocalDateTime.now(), LocalDateTime.now(), null, null
        );
        when(borrowRequestService.handoverBorrowRequest(eq(1L), eq(100L))).thenReturn(handedOver);

        mockMvc.perform(put("/api/borrow-requests/1/handover?ownerId=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HANDED_OVER"))
                .andExpect(jsonPath("$.handoverAt").isNotEmpty());

        verify(borrowRequestService, times(1)).handoverBorrowRequest(1L, 100L);
    }

    @Test
    void testHandoverBorrowRequest_NonOwner_Forbidden() throws Exception {
        when(borrowRequestService.handoverBorrowRequest(eq(1L), eq(999L)))
                .thenThrow(new UnauthorizedActionException("Only the book owner is authorized to confirm handover"));

        mockMvc.perform(put("/api/borrow-requests/1/handover?ownerId=999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the book owner is authorized to confirm handover"));
    }

    @Test
    void testHandoverBorrowRequest_InvalidState_Conflict() throws Exception {
        when(borrowRequestService.handoverBorrowRequest(eq(1L), eq(100L)))
                .thenThrow(new BorrowRequestStateException("Only requests in ACCEPTED status can be handed over"));

        mockMvc.perform(put("/api/borrow-requests/1/handover?ownerId=100"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only requests in ACCEPTED status can be handed over"));
    }

    @Test
    void testHandoverBorrowRequest_BookNotBorrowed_BadRequest() throws Exception {
        when(borrowRequestService.handoverBorrowRequest(eq(1L), eq(100L)))
                .thenThrow(new InvalidHandoverException("Book availability must be 'Borrowed' to complete handover"));

        mockMvc.perform(put("/api/borrow-requests/1/handover?ownerId=100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Book availability must be 'Borrowed' to complete handover"));
    }

    // Return Request Controller Tests
    @Test
    void testRequestReturn_Success() throws Exception {
        BorrowRequest returnRequested = new BorrowRequest(
                1L, 1L, 200L, "14 days", "Interested", BorrowRequest.STATUS_RETURN_REQUESTED,
                LocalDateTime.now(), LocalDateTime.now().minusDays(3), LocalDateTime.now(), null
        );
        when(borrowRequestService.requestReturn(eq(1L), eq(200L))).thenReturn(returnRequested);

        mockMvc.perform(put("/api/borrow-requests/1/return?borrowerId=200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURN_REQUESTED"))
                .andExpect(jsonPath("$.returnRequestedAt").isNotEmpty());

        verify(borrowRequestService, times(1)).requestReturn(1L, 200L);
    }

    @Test
    void testRequestReturn_NonBorrower_Forbidden() throws Exception {
        when(borrowRequestService.requestReturn(eq(1L), eq(999L)))
                .thenThrow(new UnauthorizedActionException("Only the borrower is authorized to request return"));

        mockMvc.perform(put("/api/borrow-requests/1/return?borrowerId=999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the borrower is authorized to request return"));
    }

    @Test
    void testRequestReturn_InvalidState_Conflict() throws Exception {
        when(borrowRequestService.requestReturn(eq(1L), eq(200L)))
                .thenThrow(new BorrowRequestStateException("Only requests in HANDED_OVER status can be requested for return"));

        mockMvc.perform(put("/api/borrow-requests/1/return?borrowerId=200"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only requests in HANDED_OVER status can be requested for return"));
    }

    // Return Confirmation Controller Tests
    @Test
    void testConfirmReturn_Success() throws Exception {
        BorrowRequest returned = new BorrowRequest(
                1L, 1L, 200L, "14 days", "Interested", BorrowRequest.STATUS_RETURNED,
                LocalDateTime.now(), LocalDateTime.now().minusDays(5), LocalDateTime.now().minusHours(1), LocalDateTime.now()
        );
        when(borrowRequestService.confirmReturn(eq(1L), eq(100L))).thenReturn(returned);

        mockMvc.perform(put("/api/borrow-requests/1/return-confirm?ownerId=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.returnedAt").isNotEmpty());

        verify(borrowRequestService, times(1)).confirmReturn(1L, 100L);
    }

    @Test
    void testConfirmReturn_NonOwner_Forbidden() throws Exception {
        when(borrowRequestService.confirmReturn(eq(1L), eq(999L)))
                .thenThrow(new UnauthorizedActionException("Only the book owner is authorized to confirm return"));

        mockMvc.perform(put("/api/borrow-requests/1/return-confirm?ownerId=999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the book owner is authorized to confirm return"));
    }

    @Test
    void testConfirmReturn_InvalidState_Conflict() throws Exception {
        when(borrowRequestService.confirmReturn(eq(1L), eq(100L)))
                .thenThrow(new BorrowRequestStateException("Only requests in RETURN_REQUESTED status can be confirmed as returned"));

        mockMvc.perform(put("/api/borrow-requests/1/return-confirm?ownerId=100"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only requests in RETURN_REQUESTED status can be confirmed as returned"));
    }
}
