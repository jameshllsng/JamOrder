package br.com.jamorder.purchaserequest.api;

import br.com.jamorder.purchaserequest.service.PurchaseRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-requests")
public class PurchaseRequestController {

    private final PurchaseRequestService purchaseRequestService;

    public PurchaseRequestController(PurchaseRequestService purchaseRequestService) {
        this.purchaseRequestService = purchaseRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseRequestResponse create(@Valid @RequestBody CreatePurchaseRequestRequest request, Authentication authentication) {
        return purchaseRequestService.create(request, authentication.getName());
    }

    @GetMapping
    public List<PurchaseRequestSummaryResponse> findAll() {
        return purchaseRequestService.findAll();
    }

    @GetMapping("/{id}")
    public PurchaseRequestResponse getById(@PathVariable long id) {
        return purchaseRequestService.getById(id);
    }

    @PutMapping("/{id}")
    public PurchaseRequestResponse update(
            @PathVariable long id,
            @Valid @RequestBody UpdatePurchaseRequestRequest request, Authentication authentication) {
        return purchaseRequestService.update(id, request, authentication.getName());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable long id, Authentication authentication) { purchaseRequestService.cancel(id, authentication.getName()); }

    @PostMapping("/{requestId}/items/{itemId}/receipts")
    public PurchaseRequestResponse registerReceipt(@PathVariable long requestId, @PathVariable long itemId, @Valid @RequestBody RegisterItemReceiptRequest request, Authentication authentication) {
        return purchaseRequestService.registerReceipt(requestId, itemId, request, authentication.getName());
    }

    @GetMapping("/{id}/history")
    public List<PurchaseRequestHistoryResponse> findHistory(@PathVariable long id) { return purchaseRequestService.findHistory(id); }

    /** Moves the requisition to the trash (admins only); it can be restored later. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moveToTrash(@PathVariable long id, Authentication authentication) {
        purchaseRequestService.moveToTrash(id, authentication.getName());
    }

    @GetMapping("/trash")
    public List<DeletedPurchaseRequestResponse> findInTrash() {
        return purchaseRequestService.findInTrash();
    }

    @PostMapping("/{id}/restore")
    public PurchaseRequestResponse restoreFromTrash(@PathVariable long id, Authentication authentication) {
        return purchaseRequestService.restoreFromTrash(id, authentication.getName());
    }
}
