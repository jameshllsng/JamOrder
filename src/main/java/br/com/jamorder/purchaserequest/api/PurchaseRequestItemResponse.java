package br.com.jamorder.purchaserequest.api;

import br.com.jamorder.purchaserequest.domain.PurchaseRequestItemStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseRequestItemResponse(
        Long id,
        String description,
        String brandModel,
        int quantity,
        String supplierName,
        BigDecimal totalValue,
        int receivedQuantity,
        LocalDate receivedOn,
        String checkedBy,
        PurchaseRequestItemStatus status,
        String notes) {
}
