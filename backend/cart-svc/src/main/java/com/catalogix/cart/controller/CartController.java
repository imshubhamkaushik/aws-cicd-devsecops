package com.catalogix.cart.controller;

import com.catalogix.cart.dto.*;
import com.catalogix.cart.svc.CartSvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartSvc svc;

    public CartController(CartSvc svc) {
        this.svc = svc;
    }

    @GetMapping
    public CartResponse get(HttpServletRequest request) {
        return svc.getOrCreateCart(userId(request), bearer(request));
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest req, HttpServletRequest request) {
        return svc.addItem(userId(request), req, bearer(request));
    }

    @PatchMapping("/items/{productId}")
    public CartResponse updateItem(@PathVariable Long productId, @Valid @RequestBody UpdateCartItemRequest req,
                                    HttpServletRequest request) {
        return svc.updateItemQuantity(userId(request), productId, req, bearer(request));
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable Long productId, HttpServletRequest request) {
        return svc.removeItem(userId(request), productId, bearer(request));
    }

    // Internal: called by checkout-svc with the caller's forwarded token to fetch
    // the cart contents at checkout.
    @GetMapping("/handoff")
    public CheckoutHandoff handoff(HttpServletRequest request) {
        return svc.toCheckoutHandoff(userId(request));
    }

    // Internal: called by checkout-svc once the order built from this cart is persisted.
    @PostMapping("/clear")
    public void clear(HttpServletRequest request) {
        svc.clear(userId(request));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private String bearer(HttpServletRequest request) {
        return (String) request.getAttribute("bearerToken");
    }
}
