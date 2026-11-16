package br.com.jamorder.purchaserequest.service;

public class ReceiptQuantityExceededException extends RuntimeException {

    public ReceiptQuantityExceededException() {
        super("Received quantity exceeds the remaining quantity.");
    }
}
