package com.catalogix.checkout.client;

import org.springframework.stereotype.Component;

/**
 * Groups checkout-svc's downstream clients so the saga orchestrator keeps a
 * small, stable constructor while the service still has explicit dependencies.
 */
@Component
public class CheckoutClients {

    private final AddressClient address;
    private final CartClient cart;
    private final CatalogClient catalog;
    private final InventoryClient inventory;
    private final PaymentClient payment;
    private final RefundClient refund;

    public CheckoutClients(
            AddressClient address,
            CartClient cart,
            CatalogClient catalog,
            InventoryClient inventory,
            PaymentClient payment,
            RefundClient refund) {
        this.address = address;
        this.cart = cart;
        this.catalog = catalog;
        this.inventory = inventory;
        this.payment = payment;
        this.refund = refund;
    }

    public AddressClient address() {
        return address;
    }

    public CartClient cart() {
        return cart;
    }

    public CatalogClient catalog() {
        return catalog;
    }

    public InventoryClient inventory() {
        return inventory;
    }

    public PaymentClient payment() {
        return payment;
    }


    public RefundClient refund() {
        return refund;
    }
}
