package com.catalogix.checkout.client;

import com.catalogix.checkout.exception.ProductUnavailableException;
import com.catalogix.security.JwtService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Calls inventory-svc directly for row-locked reserve/release (negative delta reserves, positive
 * releases). Every call mints a short-lived SYSTEM token rather than forwarding the user's token,
 * so inventory-svc's /adjust endpoint can reject anything that is not a SYSTEM token and users
 * cannot bypass the checkout saga.
 */
@Component
public class InventoryClient {

    private final RestTemplate restTemplate;
    private final String inventorySvcUrl;
    private final JwtService jwtService;

    public InventoryClient(RestTemplate restTemplate, @Value("${INVENTORY_SVC_URL}") String inventorySvcUrl,
                            JwtService jwtService) {
        this.restTemplate = restTemplate;
        this.inventorySvcUrl = inventorySvcUrl;
        this.jwtService = jwtService;
    }

    @CircuitBreaker(name = "inventorySvc", fallbackMethod = "fallback")
    public void adjust(Long productId, int delta) {
        doAdjust(productId, delta, null, null);
    }

    /**
     * Idempotent variant.
     *
     * @param operationId unique id of this adjustment; inventory-svc ignores a repeat of it.
     * @param undoOf for a release: the operation id of the reservation being reversed. If that
     *        reservation never reached inventory-svc (e.g. the request timed out) nothing is
     *        added back, so releasing after an AMBIGUOUS failure is always safe.
     */
    @CircuitBreaker(name = "inventorySvc", fallbackMethod = "fallbackIdempotent")
    public void adjust(Long productId, int delta, String operationId, String undoOf) {
        doAdjust(productId, delta, operationId, undoOf);
    }

    private void doAdjust(Long productId, int delta, String operationId, String undoOf) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateSystemToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (operationId != null) {
            headers.set("X-Operation-Id", operationId);
        }
        if (undoOf != null) {
            headers.set("X-Undo-Of", undoOf);
        }
        var body = new java.util.HashMap<String, Object>();
        body.put("delta", delta);
        try {
            restTemplate.exchange(
                    inventorySvcUrl + "/inventory/" + productId + "/adjust",
                    HttpMethod.PATCH,
                    new HttpEntity<>(body, headers),
                    Void.class);
        } catch (HttpClientErrorException.Conflict e) {
            throw new ProductUnavailableException(
                    "Insufficient stock for product " + productId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductUnavailableException("No stock record for product " + productId);
        }
    }

    @SuppressWarnings("unused")
    private void fallbackIdempotent(Long productId, int delta, String operationId, String undoOf, Throwable t) {
        fallback(productId, delta, t);
    }

    @SuppressWarnings("unused")
    private void fallback(Long productId, int delta, Throwable t) {
        throw new ProductUnavailableException(
                "Inventory is temporarily unavailable, try again shortly (product " + productId + ")");
    }
}
