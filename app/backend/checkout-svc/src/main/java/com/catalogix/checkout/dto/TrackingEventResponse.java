package com.catalogix.checkout.dto;

import com.catalogix.checkout.model.OrderStatus;
import java.time.Instant;

public record TrackingEventResponse(OrderStatus status, String note, Instant createdAt) {}
