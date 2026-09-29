package br.com.jamorder.purchaserequest.repository;

import br.com.jamorder.purchaserequest.domain.PurchaseRequestHistoryEntry;

import java.util.List;

public interface PurchaseRequestHistoryRepository {
    void insert(long purchaseRequestId, String action, String performedBy, String details);
    List<PurchaseRequestHistoryEntry> findByPurchaseRequestId(long purchaseRequestId);
}
