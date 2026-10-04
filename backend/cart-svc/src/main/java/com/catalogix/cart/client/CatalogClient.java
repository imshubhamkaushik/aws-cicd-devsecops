package com.catalogix.cart.client;

import com.catalogix.cart.exception.ProductUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Reads a product's name/price for display. catalog-svc, not cart-svc, is
 * the source of truth for both — a cart line never carries its own price;
 * it's re-fetched live every time the cart is read.
 */
@Component
public class CatalogClient {

    private final RestTemplate restTemplate;
    private final String catalogSvcUrl;

    public CatalogClient(RestTemplate restTemplate,
                          @Value("${CATALOG_SVC_URL}") String catalogSvcUrl) {
        this.restTemplate = restTemplate;
        this.catalogSvcUrl = catalogSvcUrl;
    }

    @CircuitBreaker(name = "catalogSvc", fallbackMethod = "fallback")
    public ProductInfo fetch(Long productId, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        try {
            var resp = restTemplate.exchange(
                    catalogSvcUrl + "/products/" + productId,
                    HttpMethod.GET, new HttpEntity<>(headers), ProductInfo.class);
            return resp.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductUnavailableException("Product not found: " + productId);
        }
    }

    @SuppressWarnings("unused")
    private ProductInfo fallback(Long productId, String bearerToken, Throwable t) {
        throw new ProductUnavailableException(
                "Catalog is temporarily unavailable, try again shortly (product " + productId + ")");
    }
}
