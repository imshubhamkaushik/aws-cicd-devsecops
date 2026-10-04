package com.catalogix.checkout.dto;

import java.math.BigDecimal;

public record InvoiceLineResponse(String productName, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
