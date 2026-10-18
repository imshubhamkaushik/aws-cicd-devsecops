package com.catalogix.cart.controller;

import com.catalogix.cart.dto.AddCartItemRequest;
import com.catalogix.cart.dto.CartItemResponse;
import com.catalogix.cart.dto.CartResponse;
import com.catalogix.cart.dto.CheckoutHandoff;
import com.catalogix.cart.dto.CartItemLine;
import com.catalogix.cart.dto.UpdateCartItemRequest;
import com.catalogix.cart.svc.CartSvc;
import com.catalogix.security.JwtAuthFilter;
import com.catalogix.security.RateLimiterFilter;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CartController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthFilter.class, RateLimiterFilter.class}))
class CartControllerTest {
    private static final String TOKEN = "Bearer token";

    @Autowired JsonMapper mapper;
    @Autowired MockMvc mvc;
    @MockitoBean CartSvc svc;

    private CartResponse emptyCart() {
        return new CartResponse(List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void getReturnsCurrentCart() throws Exception {
        when(svc.getOrCreateCart(42L, TOKEN)).thenReturn(emptyCart());
        mvc.perform(get("/cart").requestAttr("userId", 42L).requestAttr("bearerToken", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void addItemReturnsUpdatedCart() throws Exception {
        AddCartItemRequest req = new AddCartItemRequest();
        req.setProductId(1L); req.setQuantity(2);
        CartItemResponse item = new CartItemResponse(1L, "Phone", 2, new BigDecimal("100.00"),
                new BigDecimal("200.00"), 10);
        when(svc.addItem(eq(42L), any(AddCartItemRequest.class), eq(TOKEN)))
                .thenReturn(new CartResponse(List.of(item), new BigDecimal("200.00"), new BigDecimal("200.00")));
        mvc.perform(post("/cart/items").requestAttr("userId", 42L).requestAttr("bearerToken", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].productName").value("Phone"));
    }

    @Test
    void addItemRejectsInvalidPayload() throws Exception {
        AddCartItemRequest req = new AddCartItemRequest();
        req.setQuantity(0);
        mvc.perform(post("/cart/items").requestAttr("userId", 42L).requestAttr("bearerToken", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateItemReturnsUpdatedCart() throws Exception {
        UpdateCartItemRequest req = new UpdateCartItemRequest(); req.setQuantity(5);
        CartItemResponse item = new CartItemResponse(1L, "Phone", 5, new BigDecimal("100.00"),
                new BigDecimal("500.00"), 10);
        when(svc.updateItemQuantity(eq(42L), eq(1L), any(UpdateCartItemRequest.class), eq(TOKEN)))
                .thenReturn(new CartResponse(List.of(item), new BigDecimal("500.00"), new BigDecimal("500.00")));
        mvc.perform(patch("/cart/items/1").requestAttr("userId", 42L).requestAttr("bearerToken", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    @Test
    void removeItemReturnsUpdatedCart() throws Exception {
        when(svc.removeItem(42L, 1L, TOKEN)).thenReturn(emptyCart());
        mvc.perform(delete("/cart/items/1").requestAttr("userId", 42L).requestAttr("bearerToken", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void handoffReturnsItemsForCheckout() throws Exception {
        when(svc.toCheckoutHandoff(42L)).thenReturn(new CheckoutHandoff(List.of(new CartItemLine(1L, 2))));
        mvc.perform(get("/cart/handoff").requestAttr("userId", 42L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].productId").value(1L));
    }

    @Test
    void clearReturnsOk() throws Exception {
        mvc.perform(post("/cart/clear").requestAttr("userId", 42L))
                .andExpect(status().isOk());
        verify(svc).clear(42L);
    }
}
