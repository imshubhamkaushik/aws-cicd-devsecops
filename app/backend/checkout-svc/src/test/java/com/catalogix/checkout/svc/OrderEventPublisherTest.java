package com.catalogix.checkout.svc;

import com.catalogix.checkout.event.OrderCancelledEvent;
import com.catalogix.checkout.event.OrderConfirmedEvent;
import com.catalogix.checkout.event.OrderItemEventData;
import com.catalogix.checkout.model.OrderEventOutbox;
import com.catalogix.checkout.model.OrderEventOutboxStatus;
import com.catalogix.checkout.repository.OrderEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderEventPublisherTest {

    @Mock private OrderEventOutboxRepository repository;
    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(repository.save(any(OrderEventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        publisher = new OrderEventPublisher(repository, new JsonMapper());
    }

    @Test
    void orderConfirmationIsDurablyEnqueuedAsJson() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                5L, 1L, "buyer@example.com",
                List.of(new OrderItemEventData(10L, 20L, "Phone", 2,
                        new BigDecimal("100.00"), new BigDecimal("200.00"))),
                new BigDecimal("200.00"));

        publisher.onOrderConfirmed(event);

        ArgumentCaptor<OrderEventOutbox> captor = ArgumentCaptor.forClass(OrderEventOutbox.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRoutingKey()).isEqualTo("order.confirmed");
        assertThat(captor.getValue().getPayload()).contains("buyer@example.com", "200.00");
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderEventOutboxStatus.PENDING);
    }

    @Test
    void orderCancellationIsDurablyEnqueued() {
        publisher.onOrderCancelled(new OrderCancelledEvent(5L, 1L, "buyer@example.com"));
        ArgumentCaptor<OrderEventOutbox> captor = ArgumentCaptor.forClass(OrderEventOutbox.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRoutingKey()).isEqualTo("order.cancelled");
        assertThat(captor.getValue().getPayload()).contains("buyer@example.com");
    }

    @Test
    void anOutboxPersistenceFailureIsSurfacedSoTheOrderTransactionCanRollBack() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).save(any(OrderEventOutbox.class));
        assertThrows(IllegalStateException.class,
                () -> publisher.onOrderCancelled(new OrderCancelledEvent(5L, 1L, "buyer@example.com")));
    }
}
