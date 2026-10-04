package com.catalogix.inventory.controller;

import com.catalogix.inventory.dto.AdjustInventoryRequest;
import com.catalogix.inventory.dto.InitInventoryRequest;
import com.catalogix.inventory.dto.InventoryResponse;
import com.catalogix.inventory.exception.InsufficientInventoryException;
import com.catalogix.inventory.exception.InventoryItemNotFoundException;
import com.catalogix.security.JwtAuthFilter;
import com.catalogix.security.RateLimiterFilter;
import com.catalogix.inventory.svc.InventorySvc;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Auth is exercised via requestAttr(...) (simulating what JwtAuthFilter would set) rather
// than via a real token, so JwtAuthFilter/RateLimiterFilter are excluded from this slice —
// they'd otherwise need a real JwtService bean (JWT_SECRET etc.) just to construct.
// This is an internal, non-gateway-routed service (see InventoryController's Javadoc).
// adjust() and init() specifically require userRole=SYSTEM. GET remains
// readable without a privileged role because it does not mutate state.
@WebMvcTest(
        controllers = InventoryController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, RateLimiterFilter.class}))
class InventoryControllerTest {

    @Autowired
    private JsonMapper mapper;

    @MockitoBean
    private InventorySvc svc;

    @Autowired
    private MockMvc mvc;

    @Test
    void getReturnsStockForKnownProduct() throws Exception {
        when(svc.get(1L)).thenReturn(new InventoryResponse(1L, 10));

        mvc.perform(get("/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void getReturnsNotFoundForUnknownProduct() throws Exception {
        when(svc.get(99L)).thenThrow(new InventoryItemNotFoundException(99L));

        mvc.perform(get("/inventory/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void initCreatesStockRecord() throws Exception {
        InitInventoryRequest req = new InitInventoryRequest();
        req.setProductId(1L);
        req.setQuantity(10);
        when(svc.init(1L, 10)).thenReturn(new InventoryResponse(1L, 10));

        mvc.perform(post("/inventory")
                .requestAttr("userRole", "SYSTEM")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void initRejectsMissingProductId() throws Exception {
        InitInventoryRequest req = new InitInventoryRequest();
        req.setQuantity(10);

        mvc.perform(post("/inventory")
                .requestAttr("userRole", "SYSTEM")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initRejectsNegativeQuantity() throws Exception {
        InitInventoryRequest req = new InitInventoryRequest();
        req.setProductId(1L);
        req.setQuantity(-1);

        mvc.perform(post("/inventory")
                .requestAttr("userRole", "SYSTEM")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initRejectsNonSystemCaller() throws Exception {
        InitInventoryRequest req = new InitInventoryRequest();
        req.setProductId(1L);
        req.setQuantity(10);

        mvc.perform(post("/inventory")
                .requestAttr("userRole", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adjustReservesStockOnNegativeDelta() throws Exception {
        AdjustInventoryRequest req = new AdjustInventoryRequest();
        req.setDelta(-2);
        when(svc.adjust(1L, -2)).thenReturn(new InventoryResponse(1L, 8));

        mvc.perform(patch("/inventory/1/adjust")
                .requestAttr("userRole", "SYSTEM")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8));
    }

    @Test
    void adjustReturnsConflictWhenStockInsufficient() throws Exception {
        AdjustInventoryRequest req = new AdjustInventoryRequest();
        req.setDelta(-100);
        when(svc.adjust(1L, -100)).thenThrow(new InsufficientInventoryException(1L, 8, 100));

        mvc.perform(patch("/inventory/1/adjust")
                .requestAttr("userRole", "SYSTEM")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void adjustReturnsNotFoundForUnknownProduct() throws Exception {
        AdjustInventoryRequest req = new AdjustInventoryRequest();
        req.setDelta(-1);
        when(svc.adjust(99L, -1)).thenThrow(new InventoryItemNotFoundException(99L));

        mvc.perform(patch("/inventory/99/adjust")
                .requestAttr("userRole", "SYSTEM")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // adjust() is reachable only with the SYSTEM tokens minted by checkout-svc and catalog-svc.
    @Test
    void adjustRejectsNonSystemCaller() throws Exception {
        AdjustInventoryRequest req = new AdjustInventoryRequest();
        req.setDelta(-2);

        mvc.perform(patch("/inventory/1/adjust")
                .requestAttr("userRole", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
