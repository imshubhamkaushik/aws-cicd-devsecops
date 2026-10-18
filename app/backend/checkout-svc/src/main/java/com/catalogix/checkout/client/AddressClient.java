package com.catalogix.checkout.client;

import com.catalogix.checkout.exception.AddressUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Read-only view of user-svc's address book, used once at order placement to snapshot the
 * chosen address onto the order. Forwards the caller's own bearer token rather than a SYSTEM
 * token: reading your own address is not privileged and user-svc already scopes it to the token.
 */
@Component
public class AddressClient {

    private final RestTemplate restTemplate;
    private final String userSvcUrl;

    public AddressClient(RestTemplate restTemplate, @Value("${USER_SVC_URL}") String userSvcUrl) {
        this.restTemplate = restTemplate;
        this.userSvcUrl = userSvcUrl;
    }

    public record AddressDto(
            String label, String line1, String line2,
            String city, String state, String pincode, String phone) {}

    @CircuitBreaker(name = "userSvc", fallbackMethod = "fallback")
    public AddressDto fetch(Long addressId, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        try {
            var resp = restTemplate.exchange(
                    userSvcUrl + "/users/me/addresses/" + addressId,
                    HttpMethod.GET, new HttpEntity<>(headers), RawAddress.class);
            RawAddress body = resp.getBody();
            if (body == null) {
                throw new IllegalStateException("user-svc returned an empty address response");
            }
            return new AddressDto(body.label, body.line1, body.line2, body.city,
                    body.state, body.pincode, body.phone);
        } catch (HttpClientErrorException.NotFound e) {
            throw new AddressUnavailableException("Address not found: " + addressId);
        }
    }

    @SuppressWarnings("unused")
    private AddressDto fallback(Long addressId, String bearerToken, Throwable t) {
        throw new AddressUnavailableException(
                "Could not look up address " + addressId + " right now, try again shortly");
    }

    static class RawAddress {
        public String label;
        public String line1;
        public String line2;
        public String city;
        public String state;
        public String pincode;
        public String phone;
    }
}
