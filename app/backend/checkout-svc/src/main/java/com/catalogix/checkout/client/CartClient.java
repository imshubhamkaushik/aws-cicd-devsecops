package com.catalogix.checkout.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Only used by the /orders/checkout entrypoint (as opposed to the direct
 * POST /orders API, which still accepts an explicit item list and never
 * touches cart-svc at all).
 */
@Component
public class CartClient {

    private final RestTemplate restTemplate;
    private final String cartSvcUrl;

    public CartClient(RestTemplate restTemplate, @Value("${CART_SVC_URL}") String cartSvcUrl) {
        this.restTemplate = restTemplate;
        this.cartSvcUrl = cartSvcUrl;
    }

    public record ItemLine(Long productId, Integer quantity) {}
    public record Handoff(List<ItemLine> items) {}

    public Handoff handoff(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        var resp = restTemplate.exchange(
                cartSvcUrl + "/cart/handoff",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Handoff.class);
        Handoff body = resp.getBody();
        if (body == null) {
            throw new IllegalStateException("cart-svc returned an empty checkout handoff");
        }
        return body;
    }

    public void clear(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        restTemplate.exchange(cartSvcUrl + "/cart/clear", HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);
    }
}
