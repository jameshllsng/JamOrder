package br.com.jamorder.purchaserequest.domain;

import java.time.Instant;
import java.time.LocalDate;

public record PurchaseRequest(
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
        Instant createdAt,
        Instant updatedAt) {
}
