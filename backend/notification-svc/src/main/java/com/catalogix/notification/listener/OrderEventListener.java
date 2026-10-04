package com.catalogix.notification.listener;

import com.catalogix.notification.client.UserPreferenceClient;
import com.catalogix.notification.config.RabbitMQConfig;
import com.catalogix.notification.event.OrderCancelledEvent;
import com.catalogix.notification.event.OrderConfirmedEvent;
import com.catalogix.notification.event.OrderItemEventData;
import com.catalogix.notification.svc.EmailSvc;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Consumes order.confirmed / order.cancelled events from checkout-svc. This is
 * where the actual email subject/body text gets built — the events
 * themselves carry only data, no pre-rendered copy (see the event package's
 * Javadoc for the reasoning).
 */
@Component
public class OrderEventListener {

    private final EmailSvc emailSvc;
    private final UserPreferenceClient userPreferenceClient;

    public OrderEventListener(EmailSvc emailSvc, UserPreferenceClient userPreferenceClient) {
        this.emailSvc = emailSvc;
        this.userPreferenceClient = userPreferenceClient;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CONFIRMED_QUEUE)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        if (event.userEmail() == null || event.userEmail().isBlank()) {
            return;
        }
        if (!userPreferenceClient.isOrderEmailsEnabled(event.userId())) {
            return;
        }

        StringBuilder body = new StringBuilder("Thanks for your order!\n\n");
        body.append("Order #").append(event.orderId()).append("\n");
        for (OrderItemEventData item : event.items()) {
            body.append("  ").append(item.quantity()).append(" x ").append(item.productName())
                    .append(" — ").append(formatPrice(item.subtotal())).append('\n');
        }
        body.append("\nTotal: ").append(formatPrice(event.totalAmount())).append('\n');

        emailSvc.send(event.userEmail(), "Your Catalogix order #" + event.orderId() + " is confirmed", body.toString());
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (event.userEmail() == null || event.userEmail().isBlank()) {
            return;
        }
        if (!userPreferenceClient.isOrderEmailsEnabled(event.userId())) {
            return;
        }
        String body = "Your order #" + event.orderId() + " has been cancelled. "
                + "Any reserved stock has been released back to the catalogue.";
        emailSvc.send(event.userEmail(), "Your Catalogix order #" + event.orderId() + " was cancelled", body);
    }

    private String formatPrice(BigDecimal amount) {
        return "₹" + amount.setScale(2, RoundingMode.HALF_UP);
    }
}
