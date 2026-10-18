package com.catalogix.checkout.svc;

import com.catalogix.checkout.config.RabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderEventOutboxProcessorTest {

    private OrderEventOutboxStateService stateService;
    private RabbitTemplate rabbitTemplate;
    private OrderEventOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        stateService = mock(OrderEventOutboxStateService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        processor = new OrderEventOutboxProcessor(stateService, rabbitTemplate);
    }

    @Test
    void marksEntryPublishedOnlyAfterPositiveBrokerConfirmation() {
        when(stateService.claimDueBatch()).thenReturn(List.of(
                new OrderEventOutboxStateService.OrderEventTask(12L, "order.confirmed", "{\"orderId\":12}", 1)));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(RabbitMQConfig.EVENTS_EXCHANGE), eq("order.confirmed"),
                any(Message.class), any(CorrelationData.class));

        processor.publishDueEvents();

        verify(stateService).markPublished(12L);
        verify(stateService, never()).recordFailure(anyLong(), anyString());
    }

    @Test
    void retriesEntryWhenBrokerNacksPublication() {
        when(stateService.claimDueBatch()).thenReturn(List.of(
                new OrderEventOutboxStateService.OrderEventTask(13L, "order.cancelled", "{}", 1)));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker rejected"));
            return null;
        }).when(rabbitTemplate).send(eq(RabbitMQConfig.EVENTS_EXCHANGE), eq("order.cancelled"),
                any(Message.class), any(CorrelationData.class));

        processor.publishDueEvents();

        verify(stateService).recordFailure(eq(13L), contains("negatively acknowledged"));
        verify(stateService, never()).markPublished(anyLong());
    }

    @Test
    void preservesStableMessageIdForAtLeastOnceDelivery() {
        when(stateService.claimDueBatch()).thenReturn(List.of(
                new OrderEventOutboxStateService.OrderEventTask(44L, "order.confirmed", "{}", 1)));
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            org.assertj.core.api.Assertions.assertThat(message.getMessageProperties().getMessageId())
                    .isEqualTo("order-event-outbox-44");
            org.assertj.core.api.Assertions.assertThat(message.getMessageProperties().getDeliveryMode())
                    .isNotNull();
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(RabbitMQConfig.EVENTS_EXCHANGE), eq("order.confirmed"),
                any(Message.class), any(CorrelationData.class));

        processor.publishDueEvents();

        verify(stateService).markPublished(44L);
    }
}
