package com.catalogix.checkout.dto;

import com.catalogix.checkout.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// taxableValue + taxAmount == totalAmount exactly, by construction (see
// CheckoutSvc#getInvoice) — totalAmount is treated as tax-INCLUSIVE and the
// other two are derived from it, rather than adding tax on top. This is
// deliberate: totalAmount is the amount payment-svc actually captured, and
// an invoice showing a different total than what was actually charged would
// be a real inconsistency, not just a cosmetic one. taxRatePercent is a
// fixed, illustrative mock (18%, a common Indian GST slab) — not sourced
// from any real tax computation.
public class InvoiceResponse {
    private String invoiceNumber;
    private Long orderId;
    private Instant orderDate;
    private String sellerName;
    private String sellerAddress;
    private String customerEmail;
    private ShippingAddressSummary billingAddress;
    private List<InvoiceLineResponse> items;
    private BigDecimal itemsSubtotal;
    private BigDecimal taxableValue;
    private BigDecimal taxRatePercent;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    private String paymentReference;

    public InvoiceResponse() {
        // Required for framework deserialization.
    }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Instant getOrderDate() { return orderDate; }
    public void setOrderDate(Instant orderDate) { this.orderDate = orderDate; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public String getSellerAddress() { return sellerAddress; }
    public void setSellerAddress(String sellerAddress) { this.sellerAddress = sellerAddress; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public ShippingAddressSummary getBillingAddress() { return billingAddress; }
    public void setBillingAddress(ShippingAddressSummary billingAddress) { this.billingAddress = billingAddress; }
    public List<InvoiceLineResponse> getItems() { return items; }
    public void setItems(List<InvoiceLineResponse> items) { this.items = items; }
    public BigDecimal getItemsSubtotal() { return itemsSubtotal; }
    public void setItemsSubtotal(BigDecimal itemsSubtotal) { this.itemsSubtotal = itemsSubtotal; }
    public BigDecimal getTaxableValue() { return taxableValue; }
    public void setTaxableValue(BigDecimal taxableValue) { this.taxableValue = taxableValue; }
    public BigDecimal getTaxRatePercent() { return taxRatePercent; }
    public void setTaxRatePercent(BigDecimal taxRatePercent) { this.taxRatePercent = taxRatePercent; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
}
