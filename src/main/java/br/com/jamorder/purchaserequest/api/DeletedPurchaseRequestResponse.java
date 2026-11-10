package br.com.jamorder.purchaserequest.api;

import br.com.jamorder.purchaserequest.domain.PurchaseRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record DeletedPurchaseRequestResponse(
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
