package com.catalogix.checkout.svc;

import com.catalogix.checkout.client.AddressClient;

import com.catalogix.checkout.client.CartClient;

import com.catalogix.checkout.client.CatalogClient;
import com.catalogix.checkout.client.CheckoutClients;

import com.catalogix.checkout.client.InventoryClient;

import com.catalogix.checkout.client.PaymentClient;

import com.catalogix.checkout.client.RefundClient;

import com.catalogix.checkout.dto.CreateOrderRequest;

import com.catalogix.checkout.dto.InvoiceResponse;

import com.catalogix.checkout.dto.OrderItemRequest;

import com.catalogix.checkout.dto.PayOrderRequest;

import com.catalogix.checkout.event.OrderConfirmedEvent;

import com.catalogix.checkout.exception.ForbiddenException;

import com.catalogix.checkout.exception.InvalidOrderStateException;

import com.catalogix.checkout.exception.ProductUnavailableException;

import com.catalogix.checkout.exception.RefundFailedException;

import com.catalogix.checkout.model.CompensationOutbox;

import com.catalogix.checkout.model.Order;

import com.catalogix.checkout.model.OrderItem;

import com.catalogix.checkout.model.OrderStatus;

import com.catalogix.checkout.model.OrderStatusEvent;

import com.catalogix.checkout.model.PaymentMethod;

import com.catalogix.checkout.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.mockito.Mock;

import org.mockito.MockitoAnnotations;

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.transaction.support.SimpleTransactionStatus;

import org.springframework.transaction.support.TransactionCallback;

import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;

import java.time.Instant;

import java.util.List;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.*;

import static org.mockito.Mockito.*;

// Unit-level checkout tests cover the saga, idempotency, payment claims,

// compensation and authorization paths around the downstream HTTP clients.

class CheckoutSvcTest {

    @Mock
    private OrderRepository repo;

    @Mock
    private CompensationOutboxWriter outboxWriter;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private CartClient cartClient;

    @Mock
    private AddressClient addressClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RefundClient refundClient;

    private CheckoutSvc svc;

    private static final String TOKEN = "Bearer test-token";

    private static final String EMAIL = "buyer@example.com";

    @BeforeEach

    void setUp() {

        MockitoAnnotations.openMocks(this);

        CheckoutClients clients = new CheckoutClients(

                addressClient, cartClient, catalogClient, inventoryClient,

                paymentClient, refundClient);

        svc = new CheckoutSvc(repo, outboxWriter, clients, eventPublisher);

        when(repo.save(any(Order.class))).thenAnswer(inv -> {

            Order o = inv.getArgument(0);

            if (o.getId() == null)
                o.setId(1L);

            return o;

        });

    }

    private CatalogClient.ProductDto product(Long id, String name, String price, Long ownerId) {

        return new CatalogClient.ProductDto(id, name, new BigDecimal(price), ownerId);

    }

    // The most recently appended tracking-timeline entry — tests assert

    // against the real Order object's own association rather than a mock,

    // since Order.addStatusEvent is plain in-memory list mutation, not

    // something CheckoutSvc calls out to a collaborator for.

    private OrderStatusEvent lastEvent(Order order) {

        List<OrderStatusEvent> events = order.getStatusEvents();

        return events.get(events.size() - 1);

    }

    // ---- getInvoice ----

    private Order paidOrder() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CONFIRMED);

        order.setPaymentMethod(PaymentMethod.CARD);

        order.setPaymentReference("MOCK-CARD-abc");

        order.setCustomerEmail("buyer@example.com");

        return order;

    }

    @Test

    void getInvoiceThrowsForAnOrderThatWasNeverPaid() {

        Order order = pendingPaymentOrder(); // no paymentMethod set

        when(repo.findById(5L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> svc.getInvoice(5L, 42L, "USER"));

    }

    @Test

    void getInvoiceRejectsNonOwnerNonAdmin() {

        Order order = paidOrder();

        when(repo.findById(5L)).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () -> svc.getInvoice(5L, 999L, "USER"));

    }

    @Test

    void getInvoiceTaxableValuePlusTaxAlwaysEqualsTheOriginalTotal() {

        Order order = paidOrder(); // total = 200.00

        when(repo.findById(5L)).thenReturn(Optional.of(order));

        InvoiceResponse invoice = svc.getInvoice(5L, 42L, "USER");

        assertEquals(0, invoice.getTaxableValue().add(invoice.getTaxAmount())

                .compareTo(invoice.getTotalAmount()));

        assertEquals(0, new BigDecimal("200.00").compareTo(invoice.getTotalAmount()));

    }

    @Test

    void getInvoiceIncludesLineItemsAndSellerInfo() {

        Order order = paidOrder();

        when(repo.findById(5L)).thenReturn(Optional.of(order));

        InvoiceResponse invoice = svc.getInvoice(5L, 42L, "USER");

        assertEquals("INV-00000005", invoice.getInvoiceNumber());

        assertEquals("buyer@example.com", invoice.getCustomerEmail());

        assertEquals(1, invoice.getItems().size());

        assertEquals("Phone", invoice.getItems().get(0).productName());

        assertEquals(0, new BigDecimal("200.00").compareTo(invoice.getItemsSubtotal()));

        assertEquals(PaymentMethod.CARD, invoice.getPaymentMethod());

        assertEquals("MOCK-CARD-abc", invoice.getPaymentReference());

        assertNotNull(invoice.getSellerName());

    }

    @Test

    void getInvoiceAllowsAdminToViewAnyOrdersInvoice() {

        Order order = paidOrder();

        when(repo.findById(5L)).thenReturn(Optional.of(order));

        assertDoesNotThrow(() -> svc.getInvoice(5L, 999L, "ADMIN"));

    }

    private CreateOrderRequest requestFor(Long productId, int qty) {

        OrderItemRequest item = new OrderItemRequest();

        item.setProductId(productId);

        item.setQuantity(qty);

        CreateOrderRequest req = new CreateOrderRequest();

        req.setItems(List.of(item));

        return req;

    }

    // ---- createOrder (direct API) ----

    @Test

    void createOrderSucceedsEntersPendingPaymentAndSendsNoEmailYet() {

        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00", 42L));

        CheckoutSvc.OrderCreationResult result = svc.createOrder(42L, requestFor(1L, 2), TOKEN, null);

        assertTrue(result.wasNew());

        assertEquals(OrderStatus.PENDING_PAYMENT, result.order().getStatus());

        assertEquals(new BigDecimal("200.00"), result.order().getTotalAmount());

        verify(inventoryClient).adjust(eq(1L), eq(-2), any(), any());

        // Confirmation event fires on successful *payment*, not creation — see payOrder
        // tests.

        verifyNoInteractions(eventPublisher);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);

        verify(repo).save(saved.capture());

        assertEquals(1, saved.getValue().getStatusEvents().size());

        assertEquals(OrderStatus.PENDING_PAYMENT, lastEvent(saved.getValue()).getStatus());

        assertEquals("Order placed", lastEvent(saved.getValue()).getNote());

    }

    // ---- address snapshot ----

    @Test

    void createOrderSnapshotsTheAddressWhenAddressIdIsGiven() {

        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00", 42L));

        when(addressClient.fetch(7L, TOKEN)).thenReturn(new AddressClient.AddressDto(

                "Home", "221B Baker Street", null, "Chandigarh", "Punjab", "160001", "+919812345678"));

        CreateOrderRequest req = requestFor(1L, 2);

        req.setAddressId(7L);

        CheckoutSvc.OrderCreationResult result = svc.createOrder(42L, req, TOKEN, null);

        assertEquals("Chandigarh", result.order().getShippingAddress().city());

        assertEquals("160001", result.order().getShippingAddress().pincode());

    }

    @Test

    void createOrderSkipsAddressLookupWhenNoAddressIdIsGiven() {

        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00", 42L));

        CheckoutSvc.OrderCreationResult result = svc.createOrder(42L, requestFor(1L, 2), TOKEN, null);

        assertNull(result.order().getShippingAddress());

        verifyNoInteractions(addressClient);

    }

    @Test

    void createOrderCompensatesReservedStockWhenTheAddressLookupFails() {

        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00", 42L));

        when(addressClient.fetch(99L, TOKEN))

                .thenThrow(new com.catalogix.checkout.exception.AddressUnavailableException("not found"));

        CreateOrderRequest req = requestFor(1L, 2);

        req.setAddressId(99L);

        assertThrows(com.catalogix.checkout.exception.AddressUnavailableException.class,

                () -> svc.createOrder(42L, req, TOKEN, null));

        // Stock reserved before the address lookup failed must be released,

        // same as any other mid-saga failure — see compensate().

        verify(inventoryClient).adjust(eq(1L), eq(2), any(), any());

    }

    @Test

    void createOrderThrowsAndDoesNotSaveWhenStockInsufficient() {

        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00", 42L));

        doThrow(new ProductUnavailableException("Insufficient stock for product 1"))

                .when(inventoryClient).adjust(eq(1L), eq(-5), any(), any());

        CreateOrderRequest request = requestFor(1L, 5);

        assertThrows(ProductUnavailableException.class,

                () -> svc.createOrder(42L, request, TOKEN, null));

        verify(repo, never()).save(any());

    }

    @Test

    void createOrderReleasesAReservationThatFailedAmbiguously_namingTheReservationItUndoes() {

        // A timeout: checkout cannot know whether inventory-svc applied the
        // reservation.

        // It must still be released — and the release names the reservation (undoOf) so

        // inventory-svc adds stock back only if it really was applied.

        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00", 42L));

        doThrow(new RuntimeException("read timed out"))

                .when(inventoryClient).adjust(eq(1L), eq(-2), any(), isNull());

        CreateOrderRequest request = requestFor(1L, 2);

        assertThrows(RuntimeException.class, () -> svc.createOrder(42L, request, TOKEN, null));

        verify(inventoryClient).adjust(eq(1L), eq(2),

                argThat(op -> op != null && op.startsWith("release:attempt-")),

                argThat(undo -> undo != null && undo.endsWith(":reserve:0")));

        verify(repo, never()).save(any());

    }

    @Test

    void expireUnpaidOrderCancelsAndReleasesAPendingOrder() {

        Order order = new Order();

        order.setId(9L);

        order.setUserId(42L);

        order.setStatus(OrderStatus.PENDING_PAYMENT);

        order.addItem(new OrderItem(1L, "Phone", 2, new BigDecimal("100.00")));

        when(repo.findByIdForUpdate(9L)).thenReturn(Optional.of(order));

        assertTrue(svc.expireUnpaidOrder(9L));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        // Keyed by the order, so a retry of this release can never add the stock back
        // twice.

        verify(inventoryClient).adjust(eq(1L), eq(2), eq("release:order-9:0"), isNull());

        verify(repo).save(order);

    }

    @Test

    void expireUnpaidOrderLeavesAnOrderThatWasPaidInTheMeantime() {

        Order order = new Order();

        order.setId(9L);

        order.setStatus(OrderStatus.CONFIRMED);

        when(repo.findByIdForUpdate(9L)).thenReturn(Optional.of(order));

        assertFalse(svc.expireUnpaidOrder(9L));

        verifyNoInteractions(inventoryClient);

        verify(repo, never()).save(any());

    }

    @Test

    void createOrderWithMatchingIdempotencyKeyReturnsExistingOrderWithoutReReserving() {

        Order existing = new Order();

        existing.setId(9L);
        existing.setUserId(42L);
        existing.setStatus(OrderStatus.CONFIRMED);

        existing.setTotalAmount(new BigDecimal("50.00"));

        when(repo.findByUserIdAndIdempotencyKey(42L, "key-123")).thenReturn(Optional.of(existing));

        CheckoutSvc.OrderCreationResult result = svc.createOrder(42L, requestFor(1L, 2), TOKEN, "key-123");

        assertFalse(result.wasNew());

        assertEquals(9L, result.order().getId());

        verifyNoInteractions(catalogClient);

        verifyNoInteractions(inventoryClient);

        verify(repo, never()).save(any());

    }

    // ---- checkoutFromCart ----

    @Test

    void createOrderNormalizesIdempotencyKeyBeforeLookup() {

        Order existing = new Order();

        existing.setId(9L);
        existing.setUserId(42L);
        existing.setStatus(OrderStatus.CONFIRMED);

        existing.setTotalAmount(new BigDecimal("50.00"));

        when(repo.findByUserIdAndIdempotencyKey(42L, "key-123")).thenReturn(Optional.of(existing));

        CheckoutSvc.OrderCreationResult result =

                svc.createOrder(42L, requestFor(1L, 2), TOKEN, "  key-123  ");

        assertFalse(result.wasNew());

        assertEquals(9L, result.order().getId());

        verify(repo).findByUserIdAndIdempotencyKey(42L, "key-123");

    }

    @Test

    void createOrderRejectsAnIdempotencyKeyLongerThanTheDatabaseContract() {

        String key = "x".repeat(65);

        CreateOrderRequest request = requestFor(1L, 2);

        assertThrows(IllegalArgumentException.class,

                () -> svc.createOrder(42L, request, TOKEN, key));

        verifyNoInteractions(catalogClient, inventoryClient);

    }

    @Test

    void checkoutFromCartPlacesOrderFromHandoffAndClearsCartWhenNew() {

        CartClient.Handoff handoff = new CartClient.Handoff(
                List.of(new CartClient.ItemLine(1L, 2)));

        when(cartClient.handoff(TOKEN)).thenReturn(handoff);

        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00", 42L));

        CheckoutSvc.OrderCreationResult result = svc.checkoutFromCart(42L, null, TOKEN, null);

        assertTrue(result.wasNew());

        assertEquals(new BigDecimal("200.00"), result.order().getTotalAmount());

        verify(cartClient).clear(TOKEN);

    }

    @Test

    void checkoutFromCartDoesNotClearCartWhenIdempotencyKeyReturnsExistingOrder() {

        Order existing = new Order();

        existing.setId(9L);
        existing.setUserId(42L);
        existing.setStatus(OrderStatus.CONFIRMED);

        existing.setTotalAmount(new BigDecimal("50.00"));

        when(repo.findByUserIdAndIdempotencyKey(42L, "key-123")).thenReturn(Optional.of(existing));

        // checkoutFromCart always pulls the cart handoff up front, before the

        // idempotency check (which happens inside placeOrder) has a chance to

        // short-circuit — so the handoff still needs stubbing even though its

        // contents end up unused once the existing order is found.

        when(cartClient.handoff(TOKEN)).thenReturn(new CartClient.Handoff(List.of()));

        CheckoutSvc.OrderCreationResult result = svc.checkoutFromCart(42L, null, TOKEN, "key-123");

        assertFalse(result.wasNew());

        verify(cartClient, never()).clear(TOKEN);

    }

    @Test

    void checkoutFromCartStillReturnsTheOrderWhenClearingTheCartFails() {

        CartClient.Handoff handoff = new CartClient.Handoff(
                List.of(new CartClient.ItemLine(1L, 2)));

        when(cartClient.handoff(TOKEN)).thenReturn(handoff);

        when(catalogClient.fetch(1L, TOKEN)).thenReturn(product(1L, "Phone", "100.00", 42L));

        doThrow(new RuntimeException("cart-svc unreachable")).when(cartClient).clear(TOKEN);

        // Clearing the cart is best-effort: the order is already committed by

        // this point, so a failure here must not surface as an error to the

        // caller — see CheckoutSvc.checkoutFromCart's Javadoc.

        CheckoutSvc.OrderCreationResult result = svc.checkoutFromCart(42L, null, TOKEN, null);

        assertTrue(result.wasNew());

    }

    // ---- payOrder ----

    private Order pendingPaymentOrder() {

        Order order = new Order();

        order.setId(5L);
        order.setUserId(42L);
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        order.setTotalAmount(new BigDecimal("200.00"));

        order.addItem(new OrderItem(1L, "Phone", 2, new BigDecimal("100.00")));

        return order;

    }

    @Test

    void payOrderConfirmsAndNotifiesOnSuccessfulPayment() {

        Order order = pendingPaymentOrder();

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest req = new PayOrderRequest();

        req.setMethod(PaymentMethod.CARD);

        req.setCardLast4("4242");

        when(paymentClient.process(eq(5L), any(), eq(new BigDecimal("200.00")), eq(req)))

                .thenReturn(new PaymentClient.PaymentOutcome(true, "MOCK-REF", "SUCCEEDED"));

        CheckoutSvc.OrderPaymentResult result = svc.payOrder(5L, 42L, "USER", req, EMAIL);

        assertEquals(OrderStatus.CONFIRMED, result.order().getStatus());

        assertTrue(result.paymentSucceeded());

        verify(eventPublisher).publishEvent(any(OrderConfirmedEvent.class));

        verifyNoInteractions(inventoryClient);

        assertEquals(OrderStatus.CONFIRMED, lastEvent(order).getStatus());

        assertEquals("Payment confirmed", lastEvent(order).getNote());

    }

    // COD confirms the order like CARD/UPI, but the tracking note must say no money
    // moved yet.
    @Test

    void payOrderConfirmsWithPayOnDeliveryNoteForCod() {

        Order order = pendingPaymentOrder();

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest req = new PayOrderRequest();

        req.setMethod(PaymentMethod.COD);

        when(paymentClient.process(eq(5L), any(), eq(new BigDecimal("200.00")), eq(req)))

                .thenReturn(new PaymentClient.PaymentOutcome(true, null, "COD_PENDING"));

        CheckoutSvc.OrderPaymentResult result = svc.payOrder(5L, 42L, "USER", req, EMAIL);

        assertEquals(OrderStatus.CONFIRMED, result.order().getStatus());

        assertTrue(result.paymentSucceeded());

        assertEquals("Order confirmed — pay on delivery", lastEvent(order).getNote());

    }

    @Test

    void payOrderForwardsIdempotencyKeyToPaymentService() {

        Order order = pendingPaymentOrder();

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest req = new PayOrderRequest();

        req.setMethod(PaymentMethod.CARD);

        req.setCardLast4("4242");

        when(paymentClient.process(

                eq(5L), any(), eq(new BigDecimal("200.00")), eq(req), eq("pay-key-123")))

                .thenReturn(new PaymentClient.PaymentOutcome(true, "MOCK-REF", "SUCCEEDED"));

        CheckoutSvc.OrderPaymentResult result =

                svc.payOrder(5L, 42L, "USER", req, EMAIL, "pay-key-123");

        assertEquals(OrderStatus.CONFIRMED, result.order().getStatus());

        assertTrue(result.paymentSucceeded());

        verify(paymentClient).process(

                5L, 42L, new BigDecimal("200.00"), req, "pay-key-123");

    }

    @Test

    void payOrderCancelsAndReleasesStockOnDecline() {

        Order order = pendingPaymentOrder();

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest req = new PayOrderRequest();

        req.setMethod(PaymentMethod.CARD);

        req.setCardLast4("0000"); // magic decline value

        when(paymentClient.process(eq(5L), any(), any(), eq(req)))

                .thenReturn(new PaymentClient.PaymentOutcome(false, null, null));

        CheckoutSvc.OrderPaymentResult result = svc.payOrder(5L, 42L, "USER", req, EMAIL);

        assertEquals(OrderStatus.CANCELLED, result.order().getStatus());

        assertFalse(result.paymentSucceeded());

        verify(inventoryClient).adjust(eq(1L), eq(2), any(), any()); // stock released

        verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));

        assertEquals(OrderStatus.CANCELLED, lastEvent(order).getStatus());

        assertEquals("Payment declined", lastEvent(order).getNote());

    }

    @Test

    void payOrderRejectsWhenOrderNotAwaitingPayment() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CONFIRMED);

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest req = new PayOrderRequest();

        req.setMethod(PaymentMethod.CARD);

        assertThrows(InvalidOrderStateException.class, () -> svc.payOrder(5L, 42L, "USER", req, EMAIL));

        verifyNoInteractions(paymentClient);

    }

    // ---- updateStatus ----

    @Test

    void updateStatusAllowsConfirmedToShipped() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CONFIRMED);

        when(repo.findById(5L)).thenReturn(Optional.of(order));

        var resp = svc.updateStatus(5L, OrderStatus.SHIPPED);

        assertEquals(OrderStatus.SHIPPED, resp.getStatus());

        assertEquals(OrderStatus.SHIPPED, lastEvent(order).getStatus());

        assertEquals("Order shipped", lastEvent(order).getNote());

    }

    @Test

    void updateStatusRecordsADeliveredEventOnDelivery() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.SHIPPED);

        when(repo.findById(5L)).thenReturn(Optional.of(order));

        svc.updateStatus(5L, OrderStatus.DELIVERED);

        assertEquals("Order delivered", lastEvent(order).getNote());

    }

    @Test

    void updateStatusRejectsSkippingAStage() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CONFIRMED);

        when(repo.findById(5L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> svc.updateStatus(5L, OrderStatus.DELIVERED));

    }

    // ---- cancelOrder ----

    @Test

    void cancelConfirmedCardOrderRefundsBeforeReleasingSideEffects() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CONFIRMED);

        order.setPaymentMethod(PaymentMethod.CARD);

        order.setPaymentReference("MOCK-REF");

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        when(refundClient.refund(eq(5L), eq(new BigDecimal("200.00")), anyString()))

                .thenReturn(new RefundClient.RefundOutcome("REFUND-REF"));

        svc.cancelOrder(5L, 42L, "USER", EMAIL);

        verify(refundClient).refund(5L, new BigDecimal("200.00"), "cancel-order-5");

        verify(inventoryClient).adjust(eq(1L), eq(2), any(), any());

    }

    @Test

    void cancelConfirmedCardOrderFailsWhenRefundCannotBeProcessed() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CONFIRMED);

        order.setPaymentMethod(PaymentMethod.CARD);

        order.setPaymentReference("MOCK-REF");

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        when(refundClient.refund(5L, new BigDecimal("200.00"), "cancel-order-5"))

                .thenThrow(new RuntimeException("payment-svc unavailable"));

        assertThrows(RefundFailedException.class,

                () -> svc.cancelOrder(5L, 42L, "USER", EMAIL));

        verifyNoInteractions(inventoryClient);

    }

    @Test

    void cancelOrderNotesAdminCancellationSeparatelyFromCustomerCancellation() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CONFIRMED);

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        // Admin (userId 999) cancelling someone else's order (userId 42).

        svc.cancelOrder(5L, 999L, "ADMIN", EMAIL);

        assertEquals("Cancelled by admin", lastEvent(order).getNote());

    }

    @Test

    void cancelOrderRejectsShippedOrders() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.SHIPPED);

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> svc.cancelOrder(5L, 42L, "USER", EMAIL));

        verifyNoInteractions(inventoryClient);

    }

    @Test

    void cancelOrderIsANoOpIfAlreadyCancelled() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CANCELLED);

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        var resp = svc.cancelOrder(5L, 42L, "USER", EMAIL);

        assertEquals(OrderStatus.CANCELLED, resp.getStatus());

        verifyNoInteractions(inventoryClient);

        verifyNoInteractions(eventPublisher);

    }

    @Test

    void cancelOrderQueuesToOutboxWhenRestockFailsLive() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CONFIRMED);

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        doThrow(new ProductUnavailableException("unreachable"))

                .when(inventoryClient).adjust(eq(1L), eq(2), any(), any());

        var resp = svc.cancelOrder(5L, 42L, "USER", EMAIL);

        assertEquals(OrderStatus.CANCELLED, resp.getStatus());

        verify(outboxWriter).enqueue(argThat((CompensationOutbox entry) ->

        entry.getProductId().equals(1L) && entry.getDelta() == 2));

    }

    // ---- Transaction boundaries & the PAYMENT_PROCESSING claim ----

    //

    // These use a TransactionOperations that records whether a "transaction" is
    // open, so they can

    // prove — without a database — that no transaction (hence no pooled connection
    // or row lock) is

    // held while a remote service is being called. Real-Postgres behaviour is
    // covered by

    // CheckoutPaymentIntegrationTest.

    private static final class RecordingTx implements TransactionOperations {

        volatile boolean active;

        int completed;

        @Override

        public <T> T execute(TransactionCallback<T> action) {

            active = true;

            try {

                T result = action.doInTransaction(new SimpleTransactionStatus());

                completed++;

                return result;

            } finally {

                active = false;

            }

        }

    }

    private CheckoutSvc svcWith(RecordingTx tx) {

        CheckoutClients clients = new CheckoutClients(

                addressClient, cartClient, catalogClient, inventoryClient,

                paymentClient, refundClient);

        return new CheckoutSvc(repo, outboxWriter, clients, eventPublisher, tx);

    }

    private PayOrderRequest cardRequest() {

        PayOrderRequest req = new PayOrderRequest();

        req.setMethod(PaymentMethod.CARD);

        req.setCardLast4("4242");

        return req;

    }

    @Test

    void payOrderCallsPaymentServiceWithNoTransactionOpenAndTheOrderClaimed() {

        Order order = pendingPaymentOrder();

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        RecordingTx tx = new RecordingTx();

        PayOrderRequest req = cardRequest();

        boolean[] txOpenDuringCall = { true };

        OrderStatus[] statusDuringCall = new OrderStatus[1];

        Instant[] claimedAtDuringCall = new Instant[1];

        when(paymentClient.process(eq(5L), any(), any(), eq(req))).thenAnswer(inv -> {

            txOpenDuringCall[0] = tx.active;

            statusDuringCall[0] = order.getStatus();

            claimedAtDuringCall[0] = order.getPaymentStartedAt();

            return new PaymentClient.PaymentOutcome(true, "REF", "SUCCEEDED");

        });

        svcWith(tx).payOrder(5L, 42L, "USER", req, EMAIL);

        assertFalse(txOpenDuringCall[0], "payment-svc must not be called inside a transaction");

        assertEquals(OrderStatus.PAYMENT_PROCESSING, statusDuringCall[0]);

        assertNotNull(claimedAtDuringCall[0]);

        assertEquals(2, tx.completed, "one short transaction to claim, one to settle");

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());

    }

    @Test

    void payOrderRejectsASecondPaymentWhileOneIsInProgress() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest request = cardRequest();

        InvalidOrderStateException e = assertThrows(

                InvalidOrderStateException.class,

                () -> svc.payOrder(5L, 42L, "USER", request, EMAIL));

        assertTrue(e.getMessage().contains("PAYMENT_PROCESSING"));

        verifyNoInteractions(paymentClient);

    }

    @Test

    void payOrderReleasesTheClaimWhenPaymentServiceFailsWithUnknownOutcome() {

        Order order = pendingPaymentOrder();

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest req = cardRequest();

        when(paymentClient.process(eq(5L), any(), any(), eq(req), eq("pay-key")))

                .thenThrow(new IllegalStateException("payment-svc timed out"));

        assertThrows(IllegalStateException.class,

                () -> svc.payOrder(5L, 42L, "USER", req, EMAIL, "pay-key"));

        // Back to PENDING_PAYMENT so the customer can retry with the same
        // Idempotency-Key.

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());

        verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));

        verifyNoInteractions(inventoryClient); // unknown outcome must NOT release stock

    }

    @Test

    void payOrderStillConfirmsWhenTheClaimWasSweptBackToPendingDuringTheCall() {

        Order order = pendingPaymentOrder();

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest req = cardRequest();

        when(paymentClient.process(eq(5L), any(), any(), eq(req))).thenAnswer(inv -> {

            order.setStatus(OrderStatus.PENDING_PAYMENT); // PendingOrderExpiryJob released the stale claim

            return new PaymentClient.PaymentOutcome(true, "REF", "SUCCEEDED");

        });

        CheckoutSvc.OrderPaymentResult result = svc.payOrder(5L, 42L, "USER", req, EMAIL);

        assertEquals(OrderStatus.CONFIRMED, result.order().getStatus());

        assertTrue(result.paymentSucceeded());

    }

    @Test

    void payOrderRefundsALatePaymentWhenTheOrderWasCancelledInTheMeantime() {

        Order order = pendingPaymentOrder();

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest req = cardRequest();

        when(paymentClient.process(eq(5L), any(), any(), eq(req))).thenAnswer(inv -> {

            order.setStatus(OrderStatus.CANCELLED); // e.g. released, expired, then cancelled

            return new PaymentClient.PaymentOutcome(true, "REF", "SUCCEEDED");

        });

        InvalidOrderStateException e = assertThrows(InvalidOrderStateException.class,

                () -> svc.payOrder(5L, 42L, "USER", req, EMAIL));

        verify(refundClient).refund(5L, new BigDecimal("200.00"), "late-payment-5");

        assertTrue(e.getMessage().contains("refunded"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus()); // must not be resurrected to CONFIRMED

        verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));

    }

    @Test

    void payOrderDoesNotRefundALateCodSelection() {

        Order order = pendingPaymentOrder();

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        PayOrderRequest req = new PayOrderRequest();

        req.setMethod(PaymentMethod.COD);

        when(paymentClient.process(eq(5L), any(), any(), eq(req))).thenAnswer(inv -> {

            order.setStatus(OrderStatus.CANCELLED);

            return new PaymentClient.PaymentOutcome(true, null, "COD_PENDING");

        });

        assertThrows(InvalidOrderStateException.class, () -> svc.payOrder(5L, 42L, "USER", req, EMAIL));

        verifyNoInteractions(refundClient); // COD captured no money

    }

    @Test

    void createOrderCallsOtherServicesWithNoTransactionOpen() {

        RecordingTx tx = new RecordingTx();

        boolean[] savedInsideTx = { false };

        when(catalogClient.fetch(1L, TOKEN)).thenAnswer(inv -> {

            assertFalse(tx.active, "catalog-svc called inside a transaction");

            return product(1L, "Phone", "100.00", 42L);

        });

        doAnswer(inv -> {

            assertFalse(tx.active, "inventory-svc called inside a transaction");

            return null;

        }).when(inventoryClient).adjust(any(), anyInt(), any(), any());

        when(repo.save(any(Order.class))).thenAnswer(inv -> {

            savedInsideTx[0] = tx.active;

            Order o = inv.getArgument(0);

            if (o.getId() == null)
                o.setId(1L);

            return o;

        });

        CheckoutSvc.OrderCreationResult result = svcWith(tx).createOrder(42L, requestFor(1L, 2), TOKEN, null);

        assertTrue(result.wasNew());

        assertTrue(savedInsideTx[0], "the order INSERT still runs in a (short) transaction");

    }

    @Test

    void cancelOrderExplainsThatAPaymentIsInFlight() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        InvalidOrderStateException e = assertThrows(InvalidOrderStateException.class,

                () -> svc.cancelOrder(5L, 42L, "USER", EMAIL));

        assertTrue(e.getMessage().contains("being processed"));

        verifyNoInteractions(inventoryClient);

    }

    @Test

    void releaseStalePaymentClaimReturnsAnOldClaimToPendingPayment() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        order.setPaymentStartedAt(Instant.now().minusSeconds(900));

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertTrue(svc.releaseStalePaymentClaim(5L, Instant.now().minusSeconds(300)));

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());

    }

    @Test

    void releaseStalePaymentClaimLeavesAFreshClaimAlone() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        order.setPaymentStartedAt(Instant.now().minusSeconds(5));

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertFalse(svc.releaseStalePaymentClaim(5L, Instant.now().minusSeconds(300)));

        assertEquals(OrderStatus.PAYMENT_PROCESSING, order.getStatus());

    }

    @Test

    void releaseStalePaymentClaimIgnoresOrdersThatAreNoLongerProcessing() {

        Order order = pendingPaymentOrder();

        order.setStatus(OrderStatus.CONFIRMED);

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertFalse(svc.releaseStalePaymentClaim(5L, Instant.now()));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());

    }

    // ---- Same-Idempotency-Key race ----

    @Test

    void createOrderDoesNotReleaseTheSharedReservationWhenAConcurrentRequestWithTheSameKeyWon() {

        when(catalogClient.fetch(1L, TOKEN))

                .thenReturn(product(1L, "Phone", "100.00", 42L));

        when(repo.save(any(Order.class)))

                .thenThrow(new DataIntegrityViolationException("uq_orders_user_idempotency"));

        Order winner = new Order();

        winner.setId(9L);

        winner.setUserId(42L);

        winner.setStatus(OrderStatus.PENDING_PAYMENT);

        when(repo.findByUserIdAndIdempotencyKey(42L, "dbl-click"))

                .thenReturn(Optional.empty())

                .thenReturn(Optional.of(winner));

        CreateOrderRequest request = requestFor(1L, 2);

        assertThrows(

                DataIntegrityViolationException.class,

                () -> svc.createOrder(42L, request, TOKEN, "dbl-click"));

        // Only the reserve (-2). A release (+2) would return stock the winner's order

        // relies on.

        verify(inventoryClient).adjust(eq(1L), eq(-2), any(), any());

        verify(inventoryClient, never()).adjust(eq(1L), eq(2), any(), any());

        verifyNoInteractions(outboxWriter);

    }

    @Test

    void createOrderStillCompensatesWhenTheInsertFailsForSomeOtherReason() {

        when(catalogClient.fetch(1L, TOKEN))

                .thenReturn(product(1L, "Phone", "100.00", 42L));

        when(repo.save(any(Order.class)))

                .thenThrow(new DataIntegrityViolationException("some other constraint"));

        when(repo.findByUserIdAndIdempotencyKey(42L, "k-1"))

                .thenReturn(Optional.empty()); // nobody else owns it

        CreateOrderRequest request = requestFor(1L, 2);

        assertThrows(

                DataIntegrityViolationException.class,

                () -> svc.createOrder(42L, request, TOKEN, "k-1"));

        verify(inventoryClient)

                .adjust(eq(1L), eq(2), any(), any()); // stock released

    }

}
