package br.com.jamorder.purchaserequest.api;

import br.com.jamorder.purchaserequest.domain.Priority;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseRequestSummaryResponse(
        Long id,
        String requestNumber,
        String department,
        String requesterName,
        String purchaseReason,
        Priority priority,
        PurchaseRequestStatus status,
        LocalDate requestedOn,
        LocalDate totvsRequestedOn,
        int itemCount,
        int pendingItemCount,
        BigDecimal totalValue) {
}
