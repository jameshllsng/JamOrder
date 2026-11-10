package br.com.jamorder.purchaserequest.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PurchaseRequestSummary(
        Long id,
        String requestNumber,
        Long departmentId,
        String departmentName,
        String usageLocation,
        String requesterName,
        String purchaseReason,
        Priority priority,
        PurchaseRequestStatus status,
        LocalDate requestedOn,
        LocalDate totvsRequestedOn,
        int itemCount,
        int pendingItemCount,
        BigDecimal totalValue,
        Instant approvedAt,
        String approvedBy,
        String itemDescriptions) {
}
