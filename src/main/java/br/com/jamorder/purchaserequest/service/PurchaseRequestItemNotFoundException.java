package br.com.jamorder.purchaserequest.service;

public class PurchaseRequestItemNotFoundException extends RuntimeException {

    public PurchaseRequestItemNotFoundException(long requestId, long itemId) {
        super("Purchase request item not found: request " + requestId + ", item " + itemId);
    }
}
