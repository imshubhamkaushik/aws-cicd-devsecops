package com.catalogix.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * notification-svc is a pure consumer: it declares the same topic exchange
 * checkout-svc and user-svc publish to (declaration is idempotent, so whichever
 * service starts first creates it),
 * plus one durable queue per event type, bound by routing key.
 *
 * Each queue is configured to dead-letter into catalogix.events.dlq after
 * exhausting its retries (see application.properties'
 * spring.rabbitmq.listener.simple.retry.* for the retry policy) — so a
 * message that can't be processed (bad payload, or EmailSvc rethrowing after
 * a persistent SMTP failure) ends up somewhere inspectable instead of being
 * silently dropped or retried forever.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "catalogix.events";
    private static final String DLX_EXCHANGE = "catalogix.events.dlx";
    private static final String DLQ_QUEUE = "catalogix.events.dlq";

    public static final String ORDER_CONFIRMED_QUEUE = "notification.order-confirmed";
    public static final String ORDER_CANCELLED_QUEUE = "notification.order-cancelled";
    public static final String EMAIL_VERIFICATION_QUEUE = "notification.email-verification-requested";
    public static final String PASSWORD_RESET_QUEUE = "notification.password-reset-requested";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    // ---- Dead-letter side ----

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).quorum().build();
    }

    @Bean
    public Binding deadLetterBinding() {
        // A single catch-all binding: every dead-lettered message (whatever
        // its original routing key) lands in the one DLQ for manual inspection.
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("#");
    }

    // ---- Main queues ----
    // .quorum() is what actually makes these queues survive a RabbitMQ
    // node failure — clustering the broker (see the Helm StatefulSet)
    // replicates the CLUSTER, but an individual queue still lives on only
    // one node unless it's explicitly declared as a quorum queue. Without
    // this, HA infra work would silently not apply to any queue declared here.

    private Queue durableQueueWithDeadLetter(String name) {
        return QueueBuilder.durable(name)
                .quorum()
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Queue orderConfirmedQueue() {
        return durableQueueWithDeadLetter(ORDER_CONFIRMED_QUEUE);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return durableQueueWithDeadLetter(ORDER_CANCELLED_QUEUE);
    }

    @Bean
    public Queue emailVerificationQueue() {
        return durableQueueWithDeadLetter(EMAIL_VERIFICATION_QUEUE);
    }

    @Bean
    public Queue passwordResetQueue() {
        return durableQueueWithDeadLetter(PASSWORD_RESET_QUEUE);
    }

    @Bean
    public Binding orderConfirmedBinding() {
        return BindingBuilder.bind(orderConfirmedQueue()).to(eventsExchange()).with("order.confirmed");
    }

    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder.bind(orderCancelledQueue()).to(eventsExchange()).with("order.cancelled");
    }

    @Bean
    public Binding emailVerificationBinding() {
        return BindingBuilder.bind(emailVerificationQueue()).to(eventsExchange())
                .with("user.email-verification-requested");
    }

    @Bean
    public Binding passwordResetBinding() {
        return BindingBuilder.bind(passwordResetQueue()).to(eventsExchange())
                .with("user.password-reset-requested");
    }
}
