package com.catalogix.checkout.dto;

import com.catalogix.checkout.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;

// A mock payment request — no real card/UPI data is ever meaningfully
// validated or stored beyond cardLast4/upiId, purely cosmetic here (see
// payment-svc's PaymentSvc for what each method actually does).
public class PayOrderRequest {

    @NotNull(message = "method is required")
    private PaymentMethod method;

    private String cardLast4;

    private String upiId;

    public PayOrderRequest() {
        // Required for framework deserialization.
    }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
}
