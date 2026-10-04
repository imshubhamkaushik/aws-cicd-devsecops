package com.catalogix.user.controller;

import com.catalogix.user.dto.AddressRequest;
import com.catalogix.user.dto.AddressResponse;
import com.catalogix.user.exception.AddressNotFoundException;
import com.catalogix.security.JwtAuthFilter;
import com.catalogix.security.RateLimiterFilter;
import com.catalogix.user.svc.AddressSvc;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Auth is exercised via requestAttr(...) (simulating what JwtAuthFilter would set) rather
// than via a real token, so JwtAuthFilter/RateLimiterFilter are excluded from this slice —
// they'd otherwise need a real JwtService bean (JWT_SECRET etc.) just to construct.
@WebMvcTest(
        controllers = AddressController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, RateLimiterFilter.class}))
@ActiveProfiles("test")
class AddressControllerTest {

    @Autowired private JsonMapper mapper;
    @Autowired private MockMvc mvc;
    @MockitoBean private AddressSvc svc;

    private AddressRequest sampleRequest() {
        AddressRequest req = new AddressRequest();
        req.setLabel("Home");
        req.setLine1("221B Baker Street");
        req.setCity("Chandigarh");
        req.setState("Punjab");
        req.setPincode("160001");
        req.setPhone("+919812345678");
        return req;
    }

    private AddressResponse sampleResponse(Long id, boolean isDefault) {
        AddressResponse r = new AddressResponse();
        r.setId(id);
        r.setLabel("Home");
        r.setLine1("221B Baker Street");
        r.setCity("Chandigarh");
        r.setState("Punjab");
        r.setPincode("160001");
        r.setPhone("+919812345678");
        r.setDefault(isDefault);
        return r;
    }

    @Test
    void listReturnsTheCurrentUsersAddresses() throws Exception {
        when(svc.listForUser(42L)).thenReturn(List.of(sampleResponse(1L, true)));

        mvc.perform(get("/users/me/addresses").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].default").value(true));
    }

    @Test
    void createReturnsCreated() throws Exception {
        when(svc.create(eq(42L), any(AddressRequest.class))).thenReturn(sampleResponse(1L, true));

        mvc.perform(post("/users/me/addresses")
                .requestAttr("userId", 42L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createRejectsMissingLine1() throws Exception {
        AddressRequest req = sampleRequest();
        req.setLine1("");

        mvc.perform(post("/users/me/addresses")
                .requestAttr("userId", 42L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsAnInvalidPincode() throws Exception {
        AddressRequest req = sampleRequest();
        req.setPincode("abc");

        mvc.perform(post("/users/me/addresses")
                .requestAttr("userId", 42L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturnsNotFoundForAnAddressBelongingToAnotherUser() throws Exception {
        when(svc.update(eq(42L), eq(99L), any(AddressRequest.class)))
                .thenThrow(new AddressNotFoundException(99L));

        mvc.perform(put("/users/me/addresses/99")
                .requestAttr("userId", 42L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void setDefaultReturnsTheUpdatedAddress() throws Exception {
        when(svc.setDefault(42L, 2L)).thenReturn(sampleResponse(2L, true));

        mvc.perform(patch("/users/me/addresses/2/default").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.default").value(true));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mvc.perform(delete("/users/me/addresses/1").requestAttr("userId", 42L))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturnsNotFoundForAnAddressBelongingToAnotherUser() throws Exception {
        doThrow(new AddressNotFoundException(99L))
                .when(svc).delete(42L, 99L);

        mvc.perform(delete("/users/me/addresses/99").requestAttr("userId", 42L))
                .andExpect(status().isNotFound());
    }
}
