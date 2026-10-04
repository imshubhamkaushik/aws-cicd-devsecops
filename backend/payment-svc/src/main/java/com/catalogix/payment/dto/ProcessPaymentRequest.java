package com.catalogix.payment.dto;

import com.catalogix.payment.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ProcessPaymentRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    // The real end user checkout-svc is acting on behalf of. Populated
    // explicitly because checkout-svc now calls this endpoint with a system
    // token (see checkout-svc's PaymentClient) whose own subject is a
    // sentinel, not a real user id — this field is what keeps the audit
    // trail (Payment.requestedByUserId) meaningful.
    @NotNull(message = "requestedByUserId is required")
    private Long requestedByUserId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "method is required")
    private PaymentMethod method;

    // Mock only — last 4 "digits" of a card, required (and only
    // meaningful) when method=CARD. "0000" triggers a simulated decline so
    // callers can exercise the failure/compensation path deterministically.
    private String cardLast4;

    // Mock only — a UPI VPA, required (and only meaningful) when
    // method=UPI. A VPA starting with "fail@" triggers a simulated decline,
    // same spirit as cardLast4's "0000" convention.
    private String upiId;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(Long requestedByUserId) { this.requestedByUserId = requestedByUserId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
}
