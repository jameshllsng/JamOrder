package br.com.jamorder.purchaserequest.repository;

import br.com.jamorder.purchaserequest.domain.PurchaseRequestItem;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestItemStatus;

import java.time.LocalDate;

import java.util.List;

public interface PurchaseRequestItemRepository {

    PurchaseRequestItem insert(PurchaseRequestItem purchaseRequestItem);

    List<PurchaseRequestItem> findByPurchaseRequestId(long purchaseRequestId);

    java.util.Optional<PurchaseRequestItem> findByIdForUpdate(long id);

    PurchaseRequestItem registerReceipt(long id, int totalReceivedQuantity, int receiptQuantity, LocalDate receivedOn, String checkedBy, PurchaseRequestItemStatus status);

    void deleteByPurchaseRequestId(long purchaseRequestId);
}
