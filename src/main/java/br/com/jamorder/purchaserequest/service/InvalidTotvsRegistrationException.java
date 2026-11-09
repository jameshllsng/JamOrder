package br.com.jamorder.purchaserequest.service;

/**
 * Thrown when the TOTVS request number and date are inconsistent. The message is
 * user-facing (Portuguese) and returned by the API.
 */
public class InvalidTotvsRegistrationException extends RuntimeException {

    public InvalidTotvsRegistrationException(String message) {
        super(message);
    }
}
