package com.catalogix.cart.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Stock is display-only from cart-svc's perspective ("only 2 left") — it is
 * NOT reserved here. Reservation happens exactly once, at checkout-svc,
 * against inventory-svc directly. If this call fails we degrade to "unknown"
 * rather than blocking the cart from rendering.
 */
@Component
public class InventoryClient {

    private final RestTemplate restTemplate;
    private final String inventorySvcUrl;

    public InventoryClient(RestTemplate restTemplate,
                            @Value("${INVENTORY_SVC_URL}") String inventorySvcUrl) {
        this.restTemplate = restTemplate;
        this.inventorySvcUrl = inventorySvcUrl;
    }

    @CircuitBreaker(name = "inventorySvc", fallbackMethod = "fallback")
    public Integer fetchQuantity(Long productId, String bearerToken) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set(org.springframework.http.HttpHeaders.AUTHORIZATION, bearerToken);
        var resp = restTemplate.exchange(
                inventorySvcUrl + "/inventory/" + productId,
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                StockDto.class);
        StockDto body = resp.getBody();
        return body != null ? body.quantity : null;
    }

    @SuppressWarnings("unused")
    private Integer fallback(Long productId, String bearerToken, Throwable t) {
        return null; // "unknown" — the cart still renders, just without a stock hint
    }

    static class StockDto {
        public Long productId;
        public Integer quantity;
    }
}
