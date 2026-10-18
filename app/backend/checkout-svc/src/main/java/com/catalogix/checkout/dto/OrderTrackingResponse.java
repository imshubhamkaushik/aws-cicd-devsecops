package com.catalogix.checkout.dto;

import com.catalogix.checkout.model.OrderStatus;
import java.util.List;

// events is ordered oldest-first — a stepper UI just renders it top to
// bottom. currentStatus is redundant with the last event's status when
// there's at least one event, but kept explicit so the frontend never has
// to special-case an order with an empty timeline (shouldn't happen in
// practice — every order gets a PENDING_PAYMENT event the moment it's
// created — but costs nothing to be defensive about).
public record OrderTrackingResponse(Long orderId, OrderStatus currentStatus, List<TrackingEventResponse> events) {}
