package br.com.jamorder.purchaserequest.repository;

import br.com.jamorder.purchaserequest.domain.PurchaseRequestItem;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestItemStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseRequestItemRepository {

    PurchaseRequestItem insert(PurchaseRequestItem purchaseRequestItem);

    List<PurchaseRequestItem> findByPurchaseRequestId(long purchaseRequestId);

    Optional<PurchaseRequestItem> findByIdForUpdate(long id);

    PurchaseRequestItem registerReceipt(long id, int totalReceivedQuantity, int receiptQuantity, LocalDate receivedOn, String checkedBy, PurchaseRequestItemStatus status);

    /** Marks the still pending items of a cancelled requisition as cancelled; returns how many changed. */
    int cancelPendingItems(long purchaseRequestId);

    void deleteByPurchaseRequestId(long purchaseRequestId);
}
