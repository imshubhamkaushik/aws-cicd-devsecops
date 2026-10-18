package com.catalogix.checkout.svc;

import com.catalogix.checkout.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** At-least-once relay for durable order events; broker operations run outside DB transactions. */
@Service
public class OrderEventOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderEventOutboxProcessor.class);
    private final OrderEventOutboxStateService stateService;
    private final RabbitTemplate rabbitTemplate;

    public OrderEventOutboxProcessor(OrderEventOutboxStateService stateService, RabbitTemplate rabbitTemplate) {
        this.stateService = stateService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${ORDER_EVENT_OUTBOX_POLL_INTERVAL_MS:2000}")
    public void publishDueEvents() {
        for (OrderEventOutboxStateService.OrderEventTask task : stateService.claimDueBatch()) {
            try {
                MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                properties.setMessageId("order-event-outbox-" + task.id());
                properties.setHeader("outboxId", task.id());
                Message message = new Message(task.payload().getBytes(StandardCharsets.UTF_8), properties);
                CorrelationData correlation = new CorrelationData("order-event-outbox-" + task.id());
                rabbitTemplate.send(RabbitMQConfig.EVENTS_EXCHANGE, task.routingKey(), message, correlation);
                CorrelationData.Confirm confirmation = correlation.getFuture().get(10, TimeUnit.SECONDS);
                if (!confirmation.isAck()) {
                    throw new IllegalStateException("RabbitMQ negatively acknowledged the event: "
                            + confirmation.getReason());
                }
                if (correlation.getReturned() != null) {
                    throw new IllegalStateException("RabbitMQ could not route the event to a queue");
                }
                stateService.markPublished(task.id());
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                stateService.recordFailure(task.id(), safeMessage(e));
                log.warn("Order event outbox entry {} could not be published; retry state was recorded: {}",
                        task.id(), safeMessage(e));
            }
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
