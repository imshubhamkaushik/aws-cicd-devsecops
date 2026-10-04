package com.catalogix.checkout.client;

import com.catalogix.checkout.exception.ProductUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/** Price/name lookups only — catalog-svc no longer owns stock. */
@Component
public class CatalogClient {

    private final RestTemplate restTemplate;
    private final String catalogSvcUrl;

    public CatalogClient(RestTemplate restTemplate, @Value("${CATALOG_SVC_URL}") String catalogSvcUrl) {
        this.restTemplate = restTemplate;
        this.catalogSvcUrl = catalogSvcUrl;
    }

    public record ProductDto(Long id, String name, BigDecimal price, Long ownerId) {}

    @CircuitBreaker(name = "catalogSvc", fallbackMethod = "fallback")
    public ProductDto fetch(Long productId, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        try {
            var resp = restTemplate.exchange(catalogSvcUrl + "/products/" + productId,
                    HttpMethod.GET, new HttpEntity<>(headers), RawProduct.class);
            RawProduct body = resp.getBody();
            if (body == null) {
                throw new IllegalStateException("catalog-svc returned an empty product response");
            }
            return new ProductDto(body.id, body.name, body.price, body.ownerId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductUnavailableException("Product not found: " + productId);
        }
    }

    @SuppressWarnings("unused")
    private ProductDto fallback(Long productId, String bearerToken, Throwable t) {
        throw new ProductUnavailableException(
                "Catalog is temporarily unavailable, try again shortly (product " + productId + ")");
    }

    static class RawProduct {
        public Long id;
        public String name;
        public BigDecimal price;
        public Long ownerId;
    }
}
