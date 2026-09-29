package br.com.jamorder.purchaserequest.service;

public class DuplicatePurchaseRequestNumberException extends RuntimeException {

    public DuplicatePurchaseRequestNumberException() {
        super("Purchase request number already exists.");
    }
}
