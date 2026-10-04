package com.catalogix.checkout.client;

import com.catalogix.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Calls payment-svc to refund CARD/UPI orders on cancellation; COD orders have no captured
 * payment and never reach it. Uses a SYSTEM token like PaymentClient, since refunds are
 * privileged money-moving calls.
 */
@Component
public class RefundClient {

    private final RestTemplate restTemplate;
    private final String paymentSvcUrl;
    private final JwtService jwtService;

    public RefundClient(RestTemplate restTemplate, @Value("${PAYMENT_SVC_URL}") String paymentSvcUrl,
                         JwtService jwtService) {
        this.restTemplate = restTemplate;
        this.paymentSvcUrl = paymentSvcUrl;
        this.jwtService = jwtService;
    }

    public record RefundOutcome(String reference) {}

    public RefundOutcome refund(Long orderId, BigDecimal amount) {
        return refund(orderId, amount, null);
    }

    /**
     * @param idempotencyKey stable key for this logical refund (e.g. "cancel-order-42"); payment-svc
     *        returns the original refund for a repeat, so retrying a cancellation is safe.
     */
    public RefundOutcome refund(Long orderId, BigDecimal amount, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateSystemToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }

        var body = new java.util.HashMap<String, Object>();
        body.put("orderId", orderId);
        body.put("amount", amount);

        var resp = restTemplate.exchange(paymentSvcUrl + "/payments/refund", HttpMethod.POST,
                new HttpEntity<>(body, headers), RawRefund.class);
        RawRefund responseBody = resp.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("payment-svc returned an empty refund response");
        }
        return new RefundOutcome(responseBody.reference);
    }

    static class RawRefund {
        public String reference;
    }
}
