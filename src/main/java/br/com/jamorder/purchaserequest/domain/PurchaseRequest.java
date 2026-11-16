package br.com.jamorder.purchaserequest.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code departmentId} is the department that owns the requisition and scopes who can see it;
 * {@code usageLocation} is free text describing where the items will be used.
 * Approval ({@code approvedAt}/{@code approvedBy}) is informational and never changes the status.
 */
public record PurchaseRequest(
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
        Instant createdAt,
        Instant updatedAt) {
}
