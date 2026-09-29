package br.com.jamorder.purchaserequest.service;

public class PurchaseRequestNotFoundException extends RuntimeException {

    public PurchaseRequestNotFoundException(long id) {
        super("Purchase request not found: " + id);
    }
}
