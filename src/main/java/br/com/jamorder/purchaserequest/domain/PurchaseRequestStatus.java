package br.com.jamorder.purchaserequest.domain;

public enum PurchaseRequestStatus {
    OPEN,
    PENDING_APPROVAL,
    APPROVED,
    IN_PURCHASE,
    PARTIALLY_RECEIVED,
    COMPLETED,
    CANCELLED
}
