package com.catalogix.checkout.client;

import com.catalogix.checkout.dto.PayOrderRequest;
import com.catalogix.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * checkout-svc is the only caller of payment-svc. A decline (HTTP 402) is a business outcome, so
 * it is returned as a value rather than thrown. Calls use a short-lived SYSTEM token so users
 * cannot call payment-svc directly; the real user id travels in the body (requestedByUserId)
 * for the audit trail.
 */
@Component
public class PaymentClient {

    private final RestTemplate restTemplate;
    private final String paymentSvcUrl;
    private final JwtService jwtService;

    public PaymentClient(RestTemplate restTemplate, @Value("${PAYMENT_SVC_URL}") String paymentSvcUrl,
                          JwtService jwtService) {
        this.restTemplate = restTemplate;
        this.paymentSvcUrl = paymentSvcUrl;
        this.jwtService = jwtService;
    }

    public record PaymentOutcome(boolean succeeded, String reference, String status) {}

    /**
     * Overload for callers without an idempotency key.
     */
    public PaymentOutcome process(Long orderId, Long requestedByUserId, BigDecimal amount, PayOrderRequest req) {
        return process(orderId, requestedByUserId, amount, req, null);
    }

    public PaymentOutcome process(
            Long orderId,
            Long requestedByUserId,
            BigDecimal amount,
            PayOrderRequest req,
            String idempotencyKey
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateSystemToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey.trim());
        }

        var body = new java.util.HashMap<String, Object>();
        body.put("orderId", orderId);
        body.put("requestedByUserId", requestedByUserId);
        body.put("amount", amount);
        body.put("method", req.getMethod());
        body.put("cardLast4", req.getCardLast4());
        body.put("upiId", req.getUpiId());

        try {
            var resp = restTemplate.exchange(paymentSvcUrl + "/payments", HttpMethod.POST,
                    new HttpEntity<>(body, headers), RawPayment.class);
            RawPayment responseBody = resp.getBody();
            if (responseBody == null) {
                throw new IllegalStateException("payment-svc returned an empty payment response");
            }
            return new PaymentOutcome(true, responseBody.reference, responseBody.status);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
                return new PaymentOutcome(false, null, null);
            }
            throw e;
        }
    }

    static class RawPayment {
        public String reference;
        public String status;
    }
}
