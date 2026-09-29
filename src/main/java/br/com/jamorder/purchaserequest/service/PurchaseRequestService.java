package br.com.jamorder.purchaserequest.service;

import br.com.jamorder.purchaserequest.api.*;
import br.com.jamorder.purchaserequest.domain.*;
import br.com.jamorder.purchaserequest.repository.PurchaseRequestHistoryRepository;
import br.com.jamorder.purchaserequest.repository.PurchaseRequestItemRepository;
import br.com.jamorder.purchaserequest.repository.PurchaseRequestRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class PurchaseRequestService {
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestItemRepository purchaseRequestItemRepository;
    private final PurchaseRequestHistoryRepository purchaseRequestHistoryRepository;
    public PurchaseRequestService(PurchaseRequestRepository purchaseRequestRepository, PurchaseRequestItemRepository purchaseRequestItemRepository, PurchaseRequestHistoryRepository purchaseRequestHistoryRepository) { this.purchaseRequestRepository = purchaseRequestRepository; this.purchaseRequestItemRepository = purchaseRequestItemRepository; this.purchaseRequestHistoryRepository = purchaseRequestHistoryRepository; }

    @Transactional
    public PurchaseRequestResponse create(CreatePurchaseRequestRequest request, String actorUsername) {
        try {
            PurchaseRequest saved = purchaseRequestRepository.insert(new PurchaseRequest(null, request.requestNumber(), request.department(), request.requesterName(), request.requestedOn(), request.purchaseReason(), request.priority(), request.buyerName(), request.managerName(), request.costCenter(), PurchaseRequestStatus.OPEN, request.notes(), null, null));
            request.items().forEach(item -> purchaseRequestItemRepository.insert(toPurchaseRequestItem(saved.id(), item)));
            purchaseRequestHistoryRepository.insert(saved.id(), "CREATED", actorUsername, "Compra cadastrada.");
            return getById(saved.id());
        } catch (DuplicateKeyException exception) { throw new DuplicatePurchaseRequestNumberException(); }
    }
    @Transactional(readOnly = true) public List<PurchaseRequestSummaryResponse> findAll() { return purchaseRequestRepository.findAll().stream().map(this::toSummaryResponse).toList(); }
    @Transactional(readOnly = true) public PurchaseRequestResponse getById(long id) { PurchaseRequest purchaseRequest = purchaseRequestRepository.findById(id).orElseThrow(() -> new PurchaseRequestNotFoundException(id)); return toResponse(purchaseRequest, purchaseRequestItemRepository.findByPurchaseRequestId(id)); }
    @Transactional
    public PurchaseRequestResponse update(long id, UpdatePurchaseRequestRequest request, String actorUsername) {
        PurchaseRequest current = purchaseRequestRepository.findById(id).orElseThrow(() -> new PurchaseRequestNotFoundException(id)); requireOpen(current);
        try { purchaseRequestRepository.update(new PurchaseRequest(current.id(), request.requestNumber(), request.department(), request.requesterName(), request.requestedOn(), request.purchaseReason(), request.priority(), request.buyerName(), request.managerName(), request.costCenter(), current.status(), request.notes(), current.createdAt(), current.updatedAt())).orElseThrow(() -> new PurchaseRequestNotFoundException(id)); purchaseRequestItemRepository.deleteByPurchaseRequestId(id); request.items().forEach(item -> purchaseRequestItemRepository.insert(toPurchaseRequestItem(id, item))); purchaseRequestHistoryRepository.insert(id, "UPDATED", actorUsername, "Dados da compra atualizados."); return getById(id); } catch (DuplicateKeyException exception) { throw new DuplicatePurchaseRequestNumberException(); }
    }
    @Transactional
    public PurchaseRequestResponse registerReceipt(long requestId, long itemId, RegisterItemReceiptRequest request, String actorUsername) {
        PurchaseRequest purchaseRequest = purchaseRequestRepository.findById(requestId).orElseThrow(() -> new PurchaseRequestNotFoundException(requestId)); if (purchaseRequest.status() == PurchaseRequestStatus.CANCELLED) throw new PurchaseRequestNotOpenException();
        PurchaseRequestItem item = purchaseRequestItemRepository.findByIdForUpdate(itemId).orElseThrow(() -> new PurchaseRequestNotFoundException(requestId)); if (!item.purchaseRequestId().equals(requestId)) throw new PurchaseRequestNotFoundException(requestId);
        int totalReceived = item.receivedQuantity() + request.receivedQuantity(); if (totalReceived > item.quantity()) throw new IllegalArgumentException("Received quantity exceeds the remaining quantity.");
        PurchaseRequestItemStatus status = totalReceived == item.quantity() ? PurchaseRequestItemStatus.RECEIVED : PurchaseRequestItemStatus.PARTIALLY_RECEIVED;
        purchaseRequestItemRepository.registerReceipt(itemId, totalReceived, request.receivedQuantity(), request.receivedOn(), actorUsername, status);
        boolean allReceived = purchaseRequestItemRepository.findByPurchaseRequestId(requestId).stream().allMatch(existing -> existing.status() == PurchaseRequestItemStatus.RECEIVED);
        purchaseRequestRepository.updateStatus(requestId, allReceived ? PurchaseRequestStatus.COMPLETED : PurchaseRequestStatus.PARTIALLY_RECEIVED);
        purchaseRequestHistoryRepository.insert(requestId, "RECEIPT_REGISTERED", actorUsername, "Recebimento de " + request.receivedQuantity() + " unidade(s) do item " + item.description() + "."); return getById(requestId);
    }
    @Transactional(readOnly = true) public List<PurchaseRequestHistoryResponse> findHistory(long id) { if (purchaseRequestRepository.findById(id).isEmpty()) throw new PurchaseRequestNotFoundException(id); return purchaseRequestHistoryRepository.findByPurchaseRequestId(id).stream().map(entry -> new PurchaseRequestHistoryResponse(entry.action(), entry.performedBy(), entry.details(), entry.createdAt())).toList(); }
    @Transactional public void cancel(long id, String actorUsername) { PurchaseRequest current = purchaseRequestRepository.findById(id).orElseThrow(() -> new PurchaseRequestNotFoundException(id)); requireOpen(current); purchaseRequestRepository.updateStatus(id, PurchaseRequestStatus.CANCELLED); purchaseRequestHistoryRepository.insert(id, "CANCELLED", actorUsername, "Compra cancelada."); }
    @Transactional public void deletePermanently(long id) { PurchaseRequest current = purchaseRequestRepository.findById(id).orElseThrow(() -> new PurchaseRequestNotFoundException(id)); if (current.status() != PurchaseRequestStatus.CANCELLED) throw new PurchaseRequestNotOpenException(); purchaseRequestItemRepository.deleteByPurchaseRequestId(id); purchaseRequestRepository.deleteById(id); }
    private void requireOpen(PurchaseRequest purchaseRequest) { if (purchaseRequest.status() != PurchaseRequestStatus.OPEN) throw new PurchaseRequestNotOpenException(); }
    private PurchaseRequestItem toPurchaseRequestItem(long purchaseRequestId, CreatePurchaseRequestItemRequest item) { return new PurchaseRequestItem(null, purchaseRequestId, item.description(), item.brandModel(), item.quantity(), item.supplierName(), item.totalValue(), 0, null, null, PurchaseRequestItemStatus.PENDING, item.notes(), null, null); }
    private PurchaseRequestResponse toResponse(PurchaseRequest purchaseRequest, List<PurchaseRequestItem> items) { List<PurchaseRequestItemResponse> itemResponses = items.stream().map(item -> new PurchaseRequestItemResponse(item.id(), item.description(), item.brandModel(), item.quantity(), item.supplierName(), item.totalValue(), item.receivedQuantity(), item.receivedOn(), item.checkedBy(), item.status(), item.notes())).toList(); BigDecimal totalValue = items.stream().map(PurchaseRequestItem::totalValue).reduce(BigDecimal.ZERO, BigDecimal::add); return new PurchaseRequestResponse(purchaseRequest.id(), purchaseRequest.requestNumber(), purchaseRequest.department(), purchaseRequest.requesterName(), purchaseRequest.requestedOn(), purchaseRequest.purchaseReason(), purchaseRequest.priority(), purchaseRequest.buyerName(), purchaseRequest.managerName(), purchaseRequest.costCenter(), purchaseRequest.status(), purchaseRequest.notes(), totalValue, itemResponses, purchaseRequest.createdAt(), purchaseRequest.updatedAt()); }
    private PurchaseRequestSummaryResponse toSummaryResponse(PurchaseRequestSummary summary) { return new PurchaseRequestSummaryResponse(summary.id(), summary.requestNumber(), summary.department(), summary.requesterName(), summary.purchaseReason(), summary.priority(), summary.status(), summary.requestedOn(), summary.itemCount(), summary.totalValue()); }
}
