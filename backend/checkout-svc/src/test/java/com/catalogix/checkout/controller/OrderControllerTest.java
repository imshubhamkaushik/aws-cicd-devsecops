package com.catalogix.checkout.controller;

import com.catalogix.checkout.dto.*;
import com.catalogix.checkout.exception.ForbiddenException;
import com.catalogix.checkout.exception.InvalidOrderStateException;
import com.catalogix.checkout.exception.ProductUnavailableException;
import com.catalogix.checkout.model.OrderStatus;
import com.catalogix.checkout.model.PaymentMethod;
import com.catalogix.security.JwtAuthFilter;
import com.catalogix.security.RateLimiterFilter;
import com.catalogix.checkout.svc.CheckoutSvc;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Auth is exercised via requestAttr(...) (simulating what JwtAuthFilter would set) rather
// than via a real token, so JwtAuthFilter/RateLimiterFilter are excluded from this slice —
// they'd otherwise need a real JwtService bean (JWT_SECRET etc.) just to construct.
//
// This only covers OrderController, the sole browser-facing controller in checkout-svc.
// AdminController (outbox visibility) has no dedicated slice test yet — see CheckoutSvcTest
// for outbox-write coverage via CheckoutSvc itself.
@WebMvcTest(
        controllers = OrderController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, RateLimiterFilter.class}))
class OrderControllerTest {

    private static final String EMAIL = "buyer@example.com";
    private static final String TOKEN = "Bearer token";

    @Autowired
    private JsonMapper mapper;

    @MockitoBean
    private CheckoutSvc svc;

    @Autowired
    private MockMvc mvc;

    private CreateOrderRequest sampleRequest() {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item));
        return req;
    }

    private OrderResponse sampleResponse(OrderStatus status) {
        OrderItemResponse item = new OrderItemResponse(
                1L, "Phone", 2, new BigDecimal("100.00"), new BigDecimal("200.00"));
        return new OrderResponse(
                1L, 42L, status, new BigDecimal("200.00"), Instant.now(), List.of(item));
    }

    // ---- POST /orders (direct API) ----

    @Test
    @SuppressWarnings("null")
    void createReturnsCreatedWhenNewOrder() throws Exception {
        when(svc.createOrder(eq(42L), any(CreateOrderRequest.class), eq(TOKEN), isNull()))
                .thenReturn(new CheckoutSvc.OrderCreationResult(sampleResponse(OrderStatus.PENDING_PAYMENT), true));

        mvc.perform(post("/orders")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("bearerToken", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/orders/1"))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    @SuppressWarnings("null")
    void createReturnsOkNotCreatedWhenIdempotencyKeyMatchesExistingOrder() throws Exception {
        when(svc.createOrder(eq(42L), any(CreateOrderRequest.class), eq(TOKEN), eq("key-abc")))
                .thenReturn(new CheckoutSvc.OrderCreationResult(sampleResponse(OrderStatus.PENDING_PAYMENT), false));

        mvc.perform(post("/orders")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("bearerToken", TOKEN)
                .header("Idempotency-Key", "key-abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @SuppressWarnings("null")
    void createReturnsConflictWhenProductUnavailable() throws Exception {
        when(svc.createOrder(eq(42L), any(CreateOrderRequest.class), eq(TOKEN), isNull()))
                .thenThrow(new ProductUnavailableException("Insufficient stock for product 1"));

        mvc.perform(post("/orders")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("bearerToken", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @SuppressWarnings("null")
    void createRecoversFromIdempotencyKeyRaceByReturningWinningOrder() throws Exception {
        when(svc.createOrder(eq(42L), any(CreateOrderRequest.class), eq(TOKEN), eq("key-abc")))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(svc.findExistingByIdempotencyKey(42L, "key-abc"))
                .thenReturn(Optional.of(sampleResponse(OrderStatus.PENDING_PAYMENT)));

        mvc.perform(post("/orders")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("bearerToken", TOKEN)
                .header("Idempotency-Key", "key-abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    // ---- POST /orders/checkout (cart-driven) ----
    // This is what the frontend's "Checkout" button actually calls — there is
    // no separate CartController in checkout-svc; cart-svc owns the cart, and
    // CheckoutSvc.checkoutFromCart pulls its contents via CartClient.

    @Test
    @SuppressWarnings("null")
    void checkoutCreatesOrderFromCart() throws Exception {
        when(svc.checkoutFromCart(eq(42L), isNull(), eq(TOKEN), isNull()))
                .thenReturn(new CheckoutSvc.OrderCreationResult(sampleResponse(OrderStatus.PENDING_PAYMENT), true));

        mvc.perform(post("/orders/checkout")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("bearerToken", TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    // A request body with addressId must reach CheckoutSvc.
    @Test
    @SuppressWarnings("null")
    void checkoutPassesTheAddressIdThroughWhenGiven() throws Exception {
        when(svc.checkoutFromCart(eq(42L), eq(7L), eq(TOKEN), isNull()))
                .thenReturn(new CheckoutSvc.OrderCreationResult(sampleResponse(OrderStatus.PENDING_PAYMENT), true));

        CheckoutFromCartRequest body = new CheckoutFromCartRequest();
        body.setAddressId(7L);

        mvc.perform(post("/orders/checkout")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("bearerToken", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @SuppressWarnings("null")
    void checkoutRecoversFromIdempotencyKeyRaceByReturningWinningOrder() throws Exception {
        when(svc.checkoutFromCart(eq(42L), isNull(), eq(TOKEN), eq("key-abc")))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(svc.findExistingByIdempotencyKey(42L, "key-abc"))
                .thenReturn(Optional.of(sampleResponse(OrderStatus.PENDING_PAYMENT)));

        mvc.perform(post("/orders/checkout")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("bearerToken", TOKEN)
                .header("Idempotency-Key", "key-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void payRejectsMissingIdempotencyKey() throws Exception {
        PayOrderRequest req = new PayOrderRequest();
        req.setMethod(PaymentMethod.CARD);
        req.setCardLast4("4242");

        mvc.perform(post("/orders/1/pay")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("userEmail", EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(svc);
    }

    // ---- GET /orders, GET /orders/{id} ----

    @Test
    void listReturnsOwnOrdersForRegularUser() throws Exception {
        PagedResponse<OrderResponse> page = new PagedResponse<>(List.of(sampleResponse(OrderStatus.CONFIRMED)), 0, 20, 1, 1);
        when(svc.listOrders(eq(42L), eq("USER"), any(Pageable.class))).thenReturn(page);

        mvc.perform(get("/orders").requestAttr("userId", 42L).requestAttr("userRole", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @SuppressWarnings("null")
    void getOneReturnsForbiddenWhenNotOwner() throws Exception {
        when(svc.getOrder(1L, 99L, "USER"))
                .thenThrow(new ForbiddenException("You may only view or manage your own orders"));

        mvc.perform(get("/orders/1").requestAttr("userId", 99L).requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());
    }

    // ---- GET /orders/{id}/tracking ----

    @Test
    @SuppressWarnings("null")
    void trackingReturnsTheOrdersStatusTimeline() throws Exception {
        OrderTrackingResponse tracking = new OrderTrackingResponse(1L, OrderStatus.SHIPPED, List.of(
                new TrackingEventResponse(OrderStatus.PENDING_PAYMENT, "Order placed", Instant.now()),
                new TrackingEventResponse(OrderStatus.CONFIRMED, "Payment confirmed", Instant.now()),
                new TrackingEventResponse(OrderStatus.SHIPPED, "Order shipped", Instant.now())));
        when(svc.getTracking(1L, 42L, "USER")).thenReturn(tracking);

        mvc.perform(get("/orders/1/tracking").requestAttr("userId", 42L).requestAttr("userRole", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.events", hasSize(3)))
                .andExpect(jsonPath("$.events[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.events[2].note").value("Order shipped"));
    }

    @Test
    @SuppressWarnings("null")
    void trackingReturnsForbiddenWhenNotOwner() throws Exception {
        when(svc.getTracking(1L, 99L, "USER"))
                .thenThrow(new ForbiddenException("You may only view or manage your own orders"));

        mvc.perform(get("/orders/1/tracking").requestAttr("userId", 99L).requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());
    }

    // ---- GET /orders/{id}/invoice ----

    @Test
    @SuppressWarnings("null")
    void invoiceReturnsTheFullBreakdown() throws Exception {
        InvoiceResponse invoice = new InvoiceResponse();
        invoice.setInvoiceNumber("INV-00000001");
        invoice.setOrderId(1L);
        invoice.setTotalAmount(new BigDecimal("200.00"));
        invoice.setTaxableValue(new BigDecimal("169.49"));
        invoice.setTaxAmount(new BigDecimal("30.51"));
        invoice.setPaymentMethod(com.catalogix.checkout.model.PaymentMethod.CARD);
        when(svc.getInvoice(1L, 42L, "USER")).thenReturn(invoice);

        mvc.perform(get("/orders/1/invoice").requestAttr("userId", 42L).requestAttr("userRole", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-00000001"))
                .andExpect(jsonPath("$.totalAmount").value(200.00));
    }

    @Test
    @SuppressWarnings("null")
    void invoiceReturnsForbiddenWhenNotOwner() throws Exception {
        when(svc.getInvoice(1L, 99L, "USER"))
                .thenThrow(new ForbiddenException("You may only view or manage your own orders"));

        mvc.perform(get("/orders/1/invoice").requestAttr("userId", 99L).requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    void invoiceReturnsConflictForAnUnpaidOrder() throws Exception {
        when(svc.getInvoice(1L, 42L, "USER"))
                .thenThrow(new com.catalogix.checkout.exception.InvalidOrderStateException(
                        "No invoice available for order 1 — it was never paid for"));

        mvc.perform(get("/orders/1/invoice").requestAttr("userId", 42L).requestAttr("userRole", "USER"))
                .andExpect(status().isConflict());
    }

    // ---- GET /orders/verified-purchase ----

    // ---- POST /orders/{id}/pay ----

    @Test
    @SuppressWarnings("null")
    void payReturnsOkWithConfirmedOrderOnSuccess() throws Exception {
        PayOrderRequest req = new PayOrderRequest();
        req.setMethod(PaymentMethod.CARD);
        req.setCardLast4("4242");

        OrderResponse confirmed = sampleResponse(OrderStatus.CONFIRMED);
        when(svc.payOrder(eq(1L), eq(42L), eq("USER"), any(PayOrderRequest.class), eq(EMAIL), eq("pay-key-success")))
                .thenReturn(new CheckoutSvc.OrderPaymentResult(confirmed, true));

        mvc.perform(post("/orders/1/pay")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("userEmail", EMAIL)
                .header("Idempotency-Key", "pay-key-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
    }

    @Test
    @SuppressWarnings("null")
    void payForwardsTheIdempotencyKeyToCheckoutService() throws Exception {
        PayOrderRequest req = new PayOrderRequest();
        req.setMethod(PaymentMethod.CARD);
        req.setCardLast4("4242");

        when(svc.payOrder(eq(1L), eq(42L), eq("USER"), any(PayOrderRequest.class), eq(EMAIL), eq("pay-key-123")))
                .thenReturn(new CheckoutSvc.OrderPaymentResult(
                        sampleResponse(OrderStatus.CONFIRMED), true));

        mvc.perform(post("/orders/1/pay")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("userEmail", EMAIL)
                .header("Idempotency-Key", "pay-key-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
    }

    @Test
    @SuppressWarnings("null")
    void payReturnsOkWithFailedPaymentStatusOnDecline() throws Exception {
        PayOrderRequest req = new PayOrderRequest();
        req.setMethod(PaymentMethod.CARD);
        req.setCardLast4("0000");

        OrderResponse cancelled = sampleResponse(OrderStatus.CANCELLED);
        when(svc.payOrder(eq(1L), eq(42L), eq("USER"), any(PayOrderRequest.class), eq(EMAIL), eq("pay-key-decline")))
                .thenReturn(new CheckoutSvc.OrderPaymentResult(cancelled, false));

        // A decline is a legitimate business outcome, not an HTTP error — the
        // endpoint still returns 200 with payment.status = FAILED, matching
        // CheckoutSvc.payOrder/PaymentClient.process's translation of a 402
        // from payment-svc into a normal (not exceptional) return value.
        mvc.perform(post("/orders/1/pay")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("userEmail", EMAIL)
                .header("Idempotency-Key", "pay-key-decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("FAILED"));
    }

    @Test
    @SuppressWarnings("null")
    void payReturnsConflictWhenOrderNotAwaitingPayment() throws Exception {
        PayOrderRequest req = new PayOrderRequest();
        req.setMethod(PaymentMethod.CARD);

        when(svc.payOrder(eq(1L), eq(42L), eq("USER"), any(PayOrderRequest.class), eq(EMAIL), eq("pay-key-conflict")))
                .thenThrow(new InvalidOrderStateException("Order 1 is not awaiting payment"));

        mvc.perform(post("/orders/1/pay")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("userEmail", EMAIL)
                .header("Idempotency-Key", "pay-key-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ---- PATCH /orders/{id}/status ----

    @Test
    @SuppressWarnings("null")
    void updateStatusAllowsAdmin() throws Exception {
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(OrderStatus.SHIPPED);
        when(svc.updateStatus(1L, OrderStatus.SHIPPED)).thenReturn(sampleResponse(OrderStatus.SHIPPED));

        mvc.perform(patch("/orders/1/status")
                .requestAttr("userRole", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @SuppressWarnings("null")
    void updateStatusRejectsNonAdmin() throws Exception {
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(OrderStatus.SHIPPED);

        mvc.perform(patch("/orders/1/status")
                .requestAttr("userRole", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ---- PATCH /orders/{id}/cancel ----

    @Test
    @SuppressWarnings("null")
    void cancelReturnsOk() throws Exception {
        when(svc.cancelOrder(1L, 42L, "USER", EMAIL)).thenReturn(sampleResponse(OrderStatus.CANCELLED));

        mvc.perform(patch("/orders/1/cancel")
                .requestAttr("userId", 42L)
                .requestAttr("userRole", "USER")
                .requestAttr("userEmail", EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
