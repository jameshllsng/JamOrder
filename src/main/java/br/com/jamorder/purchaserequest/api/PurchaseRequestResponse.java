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
        String department,
        String requesterName,
        LocalDate requestedOn,
        LocalDate totvsRequestedOn,
        String purchaseReason,
        Priority priority,
        String costCenter,
        PurchaseRequestStatus status,
        String notes,
        BigDecimal totalValue,
        List<PurchaseRequestItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {
}
