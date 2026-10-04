package com.catalogix.catalog.client;

import com.catalogix.catalog.exception.InsufficientStockException;
import com.catalogix.security.JwtService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * catalog-svc's view of inventory-svc, used to compose stock into ProductResponse.
 * fetchQuantity is read-only and forwards the caller's bearer token. init() and adjust()
 * are mutations and mint a short-lived SYSTEM token, because inventory-svc's /adjust only
 * accepts SYSTEM tokens. Owner/admin authorization is enforced once in ProductSvc#adjustStock.
 */
@Component
public class InventoryClient {

    private final RestTemplate restTemplate;
    private final String inventorySvcUrl;
    private final JwtService jwtService;

    public InventoryClient(RestTemplate restTemplate,
            @Value("${INVENTORY_SVC_URL}") String inventorySvcUrl,
            JwtService jwtService) {
        this.restTemplate = restTemplate;
        this.inventorySvcUrl = inventorySvcUrl;
        this.jwtService = jwtService;
    }

    @CircuitBreaker(name = "inventorySvc", fallbackMethod = "fetchFallback")
    public Integer fetchQuantity(Long productId, String bearerToken) {
        HttpHeaders headers = authHeaders(bearerToken);
        var resp = restTemplate.exchange(inventorySvcUrl + "/inventory/" + productId,
                HttpMethod.GET, new HttpEntity<>(headers), StockDto.class);
        StockDto body = resp.getBody();
        return body != null ? body.quantity : null;
    }

    @SuppressWarnings("unused")
    private Integer fetchFallback(Long productId, String bearerToken, Throwable t) {
        return null; // stock shown as "unknown" rather than failing the whole product read
    }

    public void init(Long productId, int initialQuantity) {
        HttpHeaders headers = authHeaders("Bearer " + jwtService.generateSystemToken());
        var body = new java.util.HashMap<String, Object>();
        body.put("productId", productId);
        body.put("quantity", initialQuantity);
        restTemplate.exchange(inventorySvcUrl + "/inventory", HttpMethod.POST,
                new HttpEntity<>(body, headers), StockDto.class);
    }

    public Integer adjust(Long productId, int delta) {
        HttpHeaders headers = authHeaders("Bearer " + jwtService.generateSystemToken());
        var body = new java.util.HashMap<String, Object>();
        body.put("delta", delta);
        try {
            var resp = restTemplate.exchange(
                    inventorySvcUrl + "/inventory/" + productId + "/adjust",
                    HttpMethod.PATCH, new HttpEntity<>(body, headers), StockDto.class);
            StockDto responseBody = resp.getBody();
            return responseBody != null ? responseBody.quantity : null;
        } catch (HttpClientErrorException.Conflict e) {
            throw new InsufficientStockException(productId, -1, -delta);
        }
    }

    private HttpHeaders authHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    static class StockDto {
        public Long productId;
        public Integer quantity;
    }
}
