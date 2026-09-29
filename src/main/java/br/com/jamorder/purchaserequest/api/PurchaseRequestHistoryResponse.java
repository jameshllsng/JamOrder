package br.com.jamorder.purchaserequest.api;

import java.time.Instant;

public record PurchaseRequestHistoryResponse(String action, String performedBy, String details, Instant createdAt) {
}
