package br.com.jamorder.purchaserequest.api;

import br.com.jamorder.purchaserequest.domain.Priority;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseRequestResponse(
        Long id,
        String requestNumber,
        Long departmentId,
        String departmentName,
        String usageLocation,
        String requesterName,
        LocalDate requestedOn,
        LocalDate totvsRequestedOn,
        String purchaseReason,
        Priority priority,
        String costCenter,
        PurchaseRequestStatus status,
        String notes,
        Instant approvedAt,
        String approvedBy,
        BigDecimal totalValue,
        List<PurchaseRequestItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {
}
