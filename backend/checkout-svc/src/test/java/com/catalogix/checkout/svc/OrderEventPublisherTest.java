package com.catalogix.checkout.svc;

import com.catalogix.checkout.config.RabbitMQConfig;
import com.catalogix.checkout.event.OrderCancelledEvent;
import com.catalogix.checkout.event.OrderConfirmedEvent;
import com.catalogix.checkout.event.OrderItemEventData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OrderEventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new OrderEventPublisher(rabbitTemplate);
    }

    @Test
    void onOrderConfirmedPublishesToTheEventsExchangeWithTheRightRoutingKey() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                5L, 1L, "buyer@example.com",
                List.of(new OrderItemEventData(10L, 20L, "Phone", 2, new BigDecimal("100.00"), new BigDecimal("200.00"))),
                new BigDecimal("200.00"));

        publisher.onOrderConfirmed(event);

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, "order.confirmed", event);
    }

    @Test
    void onOrderCancelledPublishesToTheEventsExchangeWithTheRightRoutingKey() {
        OrderCancelledEvent event = new OrderCancelledEvent(5L, 1L, "buyer@example.com");

        publisher.onOrderCancelled(event);

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, "order.cancelled", event);
    }

    @Test
    void aBrokerFailureDuringPublishIsSwallowedNotThrown() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                5L,
                1L,
                "buyer@example.com",
                List.of(),
                BigDecimal.ZERO
        );

        doThrow(new RuntimeException("broker unreachable"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        assertDoesNotThrow(() -> publisher.onOrderConfirmed(event));

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE,
                "order.confirmed",
                event
        );
    }
}
