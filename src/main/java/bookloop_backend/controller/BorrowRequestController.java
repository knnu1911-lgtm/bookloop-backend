package bookloop_backend.controller;

import bookloop_backend.model.BorrowRequest;
import bookloop_backend.service.BorrowRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/borrow-requests")
public class BorrowRequestController {

    private final BorrowRequestService borrowRequestService;

    public BorrowRequestController(BorrowRequestService borrowRequestService) {
        this.borrowRequestService = borrowRequestService;
    }

    @PostMapping
    public ResponseEntity<BorrowRequest> createBorrowRequest(@Valid @RequestBody BorrowRequest request) {
        BorrowRequest savedRequest = borrowRequestService.createBorrowRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRequest);
    }

    @GetMapping
    public ResponseEntity<List<BorrowRequest>> getBorrowRequests(@RequestParam(required = false) Long userId) {
        List<BorrowRequest> requests = borrowRequestService.getBorrowRequests(userId);
        return ResponseEntity.ok(requests);
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<BorrowRequest> acceptBorrowRequest(
            @PathVariable Long id,
            @RequestParam Long ownerId) {
        BorrowRequest updatedRequest = borrowRequestService.acceptBorrowRequest(id, ownerId);
        return ResponseEntity.ok(updatedRequest);
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<BorrowRequest> rejectBorrowRequest(
            @PathVariable Long id,
            @RequestParam Long ownerId) {
        BorrowRequest updatedRequest = borrowRequestService.rejectBorrowRequest(id, ownerId);
        return ResponseEntity.ok(updatedRequest);
    }

    @PutMapping("/{id}/handover")
    public ResponseEntity<BorrowRequest> handoverBorrowRequest(
            @PathVariable Long id,
            @RequestParam Long ownerId) {
        BorrowRequest updatedRequest = borrowRequestService.handoverBorrowRequest(id, ownerId);
        return ResponseEntity.ok(updatedRequest);
    }

    @PutMapping("/{id}/return")
    public ResponseEntity<BorrowRequest> requestReturn(
            @PathVariable Long id,
            @RequestParam Long borrowerId) {
        BorrowRequest updatedRequest = borrowRequestService.requestReturn(id, borrowerId);
        return ResponseEntity.ok(updatedRequest);
    }

    @PutMapping("/{id}/return-confirm")
    public ResponseEntity<BorrowRequest> confirmReturn(
            @PathVariable Long id,
            @RequestParam Long ownerId) {
        BorrowRequest updatedRequest = borrowRequestService.confirmReturn(id, ownerId);
        return ResponseEntity.ok(updatedRequest);
    }
}
