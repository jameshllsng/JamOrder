package br.com.jamorder.purchaserequest.domain;

import java.time.Instant;

public record PurchaseRequestHistoryEntry(Long id, Long purchaseRequestId, String action, String performedBy, String details, Instant createdAt) {
}
