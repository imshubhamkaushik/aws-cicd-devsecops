package com.catalogix.payment.exception;

/** Thrown when a refund is requested for an order with no successful CARD/UPI payment to reverse. */
public class NoSuchPaymentException extends RuntimeException {
    public NoSuchPaymentException(Long orderId) {
        super("No successful payment found for order " + orderId + " to refund");
    }
}
