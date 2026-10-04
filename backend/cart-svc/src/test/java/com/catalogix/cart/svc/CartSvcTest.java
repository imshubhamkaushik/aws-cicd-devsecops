package com.catalogix.cart.svc;

import com.catalogix.cart.client.CatalogClient;
import com.catalogix.cart.client.InventoryClient;
import com.catalogix.cart.client.ProductInfo;
import com.catalogix.cart.dto.AddCartItemRequest;
import com.catalogix.cart.dto.CartResponse;
import com.catalogix.cart.dto.UpdateCartItemRequest;
import com.catalogix.cart.exception.EmptyCartException;
import com.catalogix.cart.exception.ProductUnavailableException;
import com.catalogix.cart.model.Cart;
import com.catalogix.cart.model.CartItem;
import com.catalogix.cart.repository.CartRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CartSvcTest {

    @Mock private CartRepository repo;
    @Mock private CatalogClient catalogClient;
    @Mock private InventoryClient inventoryClient;

    private CartSvc svc;

    private static final String TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        svc = new CartSvc(repo, catalogClient, inventoryClient);
        when(repo.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProductInfo product(Long id, String name, String price) {
        ProductInfo p = new ProductInfo();
        p.setId(id); p.setName(name); p.setPrice(new BigDecimal(price));
        return p;
    }

    // ---- getOrCreateCart ----

    @Test
    void getOrCreateCartCreatesEmptyCartWhenNoneExists() {
        when(repo.findByUserId(42L)).thenReturn(Optional.empty());

        CartResponse resp = svc.getOrCreateCart(42L, TOKEN);

        assertTrue(resp.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, resp.getSubtotal());
        verify(repo).save(any(Cart.class));
    }

    @Test
    void getOrCreateCartPropagatesWhenProductLookupFails() {
        // A failed catalog lookup is propagated as an application error.
        Cart cart = new Cart(42L);
        cart.addItem(new CartItem(1L, 2));
        when(repo.findByUserId(42L)).thenReturn(Optional.of(cart));
        when(catalogClient.fetch(1L, TOKEN)).thenThrow(new ProductUnavailableException("Product not found: 1"));

        assertThrows(ProductUnavailableException.class, () -> svc.getOrCreateCart(42L, TOKEN));
    }

    // ---- addItem ----

    @Test
    void addItemAddsNewLineAndComputesSubtotal() {
        when(repo.findByUserId(42L)).thenReturn(Optional.empty());
        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00"));
        when(inventoryClient.fetchQuantity(1L, TOKEN)).thenReturn(10);

        AddCartItemRequest req = new AddCartItemRequest();
        req.setProductId(1L);
        req.setQuantity(2);

        CartResponse resp = svc.addItem(42L, req, TOKEN);

        assertEquals(1, resp.getItems().size());
        assertEquals(new BigDecimal("200.00"), resp.getSubtotal());
        assertEquals(new BigDecimal("200.00"), resp.getTotal());
        // Fetched twice: the existence check, then response rendering.
        verify(catalogClient, times(2)).fetch(1L, TOKEN);
    }

    @Test
    void addItemFailsFastOnUnknownProductWithoutTouchingTheCart() {
        when(catalogClient.fetch(99L, TOKEN)).thenThrow(new ProductUnavailableException("Product not found: 99"));

        AddCartItemRequest req = new AddCartItemRequest();
        req.setProductId(99L);
        req.setQuantity(1);

        assertThrows(ProductUnavailableException.class, () -> svc.addItem(42L, req, TOKEN));
        verify(repo, never()).save(any());
    }

    @Test
    void addItemIncrementsQuantityWhenProductAlreadyInCart() {
        Cart cart = new Cart(42L);
        cart.addItem(new CartItem(1L, 2));
        when(repo.findByUserId(42L)).thenReturn(Optional.of(cart));
        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00"));
        when(inventoryClient.fetchQuantity(1L, TOKEN)).thenReturn(10);

        AddCartItemRequest req = new AddCartItemRequest();
        req.setProductId(1L);
        req.setQuantity(3);

        CartResponse resp = svc.addItem(42L, req, TOKEN);

        assertEquals(1, resp.getItems().size());
        assertEquals(5, resp.getItems().get(0).getQuantity());
    }

    // ---- updateItemQuantity ----

    @Test
    void updateItemQuantityChangesExistingLine() {
        Cart cart = new Cart(42L);
        cart.addItem(new CartItem(1L, 2));
        when(repo.findByUserId(42L)).thenReturn(Optional.of(cart));
        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00"));
        when(inventoryClient.fetchQuantity(1L, TOKEN)).thenReturn(10);

        UpdateCartItemRequest req = new UpdateCartItemRequest();
        req.setQuantity(5);

        CartResponse resp = svc.updateItemQuantity(42L, 1L, req, TOKEN);

        assertEquals(5, resp.getItems().get(0).getQuantity());
    }

    @Test
    void updateItemQuantityRejectsProductNotInCart() {
        Cart cart = new Cart(42L);
        when(repo.findByUserId(42L)).thenReturn(Optional.of(cart));

        UpdateCartItemRequest req = new UpdateCartItemRequest();
        req.setQuantity(5);

        // The service throws EmptyCartException (message "Product not in cart").
        assertThrows(EmptyCartException.class, () -> svc.updateItemQuantity(42L, 99L, req, TOKEN));
    }

    @Test
    void updateItemQuantityRejectsWhenCartDoesNotExistAtAll() {
        when(repo.findByUserId(42L)).thenReturn(Optional.empty());

        UpdateCartItemRequest req = new UpdateCartItemRequest();
        req.setQuantity(5);

        assertThrows(EmptyCartException.class, () -> svc.updateItemQuantity(42L, 1L, req, TOKEN));
    }

    // ---- removeItem ----

    @Test
    void removeItemRemovesTheLine() {
        Cart cart = new Cart(42L);
        cart.addItem(new CartItem(1L, 2));
        when(repo.findByUserId(42L)).thenReturn(Optional.of(cart));

        CartResponse resp = svc.removeItem(42L, 1L, TOKEN);

        assertTrue(resp.getItems().isEmpty());
        verifyNoInteractions(catalogClient);
    }

    @Test
    void removeItemRejectsWhenCartDoesNotExist() {
        when(repo.findByUserId(42L)).thenReturn(Optional.empty());
        assertThrows(EmptyCartException.class, () -> svc.removeItem(42L, 1L, TOKEN));
    }

    // ---- toCheckoutHandoff ----

    @Test
    void toCheckoutHandoffRejectsWhenCartDoesNotExist() {
        when(repo.findByUserId(42L)).thenReturn(Optional.empty());
        assertThrows(EmptyCartException.class, () -> svc.toCheckoutHandoff(42L));
    }

    @Test
    void toCheckoutHandoffRejectsAnEmptyCart() {
        when(repo.findByUserId(42L)).thenReturn(Optional.of(new Cart(42L)));
        assertThrows(EmptyCartException.class, () -> svc.toCheckoutHandoff(42L));
    }

    // ---- clear ----

    @Test
    void clearIsANoOpWhenCartDoesNotExist() {
        when(repo.findByUserId(42L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> svc.clear(42L));
        verify(repo, never()).save(any());
    }
}
