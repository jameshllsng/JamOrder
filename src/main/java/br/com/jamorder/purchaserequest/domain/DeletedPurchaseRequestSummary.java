package br.com.jamorder.purchaserequest.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record DeletedPurchaseRequestSummary(
        Long id,
        String requestNumber,
        String departmentName,
        String requesterName,
        String purchaseReason,
        PurchaseRequestStatus status,
        int itemCount,
        BigDecimal totalValue,
        Instant deletedAt,
        String deletedBy) {
}
