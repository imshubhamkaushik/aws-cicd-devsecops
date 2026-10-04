package com.catalogix.payment.svc;

import com.catalogix.payment.dto.PaymentResponse;
import com.catalogix.payment.dto.ProcessPaymentRequest;
import com.catalogix.payment.dto.ProcessRefundRequest;
import com.catalogix.payment.dto.RefundResponse;
import com.catalogix.payment.exception.DeclinedException;
import com.catalogix.payment.exception.NoSuchPaymentException;
import com.catalogix.payment.model.Payment;
import com.catalogix.payment.model.PaymentMethod;
import com.catalogix.payment.model.PaymentStatus;
import com.catalogix.payment.model.Refund;
import com.catalogix.payment.repository.PaymentRepository;
import com.catalogix.payment.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentSvcTest {

    private PaymentRepository repo;
    private RefundRepository refundRepo;
    private PaymentSvc svc;

    @BeforeEach
    void setUp() {
        repo = mock(PaymentRepository.class);
        refundRepo = mock(RefundRepository.class);
        svc = new PaymentSvc(repo, refundRepo);
        // Echo back whatever gets saved, with an id assigned, like a real repo would.
        when(repo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(repo.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(refundRepo.save(any(Refund.class))).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
    }

    private ProcessPaymentRequest cardReq(String cardLast4) {
        ProcessPaymentRequest r = new ProcessPaymentRequest();
        r.setOrderId(42L);
        r.setAmount(new BigDecimal("19.99"));
        r.setMethod(PaymentMethod.CARD);
        r.setCardLast4(cardLast4);
        return r;
    }

    private ProcessPaymentRequest upiReq(String upiId) {
        ProcessPaymentRequest r = new ProcessPaymentRequest();
        r.setOrderId(42L);
        r.setAmount(new BigDecimal("19.99"));
        r.setMethod(PaymentMethod.UPI);
        r.setUpiId(upiId);
        return r;
    }

    private ProcessPaymentRequest codReq(String amount) {
        ProcessPaymentRequest r = new ProcessPaymentRequest();
        r.setOrderId(42L);
        r.setAmount(new BigDecimal(amount));
        r.setMethod(PaymentMethod.COD);
        return r;
    }

    // ---- CARD ----

    @Test
    void successfulCardPaymentReturnsSucceededWithReference() {
        PaymentResponse resp = svc.process(cardReq("4242"), 7L);

        assertThat(resp.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(resp.getReference()).startsWith("MOCK-CARD-");
        assertThat(resp.getOrderId()).isEqualTo(42L);
    }

    @Test
    void cardLast4OfZerosIsDeclined() {
        ProcessPaymentRequest req = cardReq("0000");

        assertThatThrownBy(() -> svc.process(req, 7L))
                .isInstanceOf(DeclinedException.class);
    }

    @Test
    void declinedCardAttemptIsStillPersistedForAudit() {
        ProcessPaymentRequest req = cardReq("0000");

        assertThatThrownBy(() -> svc.process(req, 7L))
                .isInstanceOf(DeclinedException.class);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(repo).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(captor.getValue().getReference()).isNull();
    }

    @Test
    void cardWithoutLast4IsRejectedBeforeAnyPersistence() {
        ProcessPaymentRequest req = cardReq(null);

        assertThatThrownBy(() -> svc.process(req, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }

    // ---- UPI ----

    @Test
    void successfulUpiPaymentReturnsSucceededWithReference() {
        PaymentResponse resp = svc.process(upiReq("buyer@upi"), 7L);

        assertThat(resp.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(resp.getReference()).startsWith("MOCK-UPI-");
    }

    @Test
    void upiIdStartingWithFailIsDeclined() {
        ProcessPaymentRequest req = upiReq("fail@upi");

        assertThatThrownBy(() -> svc.process(req, 7L))
                .isInstanceOf(DeclinedException.class);
    }

    @Test
    void upiDeclineCheckIsCaseInsensitive() {
        ProcessPaymentRequest req = upiReq("FAIL@UPI");

        assertThatThrownBy(() -> svc.process(req, 7L))
                .isInstanceOf(DeclinedException.class);
    }

    @Test
    void upiWithoutIdIsRejectedBeforeAnyPersistence() {
        ProcessPaymentRequest req = upiReq(null);

        assertThatThrownBy(() -> svc.process(req, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }

    // ---- COD ----

    @Test
    void codNeverDeclinesAndEntersPendingState() {
        PaymentResponse resp = svc.process(codReq("199.99"), 7L);

        assertThat(resp.getStatus()).isEqualTo(PaymentStatus.COD_PENDING);
        assertThat(resp.getReference()).isNull(); // nothing was actually captured
    }

    @Test
    void codAboveTheCapIsRejectedBeforeAnyPersistence() {
        ProcessPaymentRequest req = codReq("50000.01");

        assertThatThrownBy(() -> svc.process(req, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void codExactlyAtTheCapIsAllowed() {
        PaymentResponse resp = svc.process(codReq("50000.00"), 7L);

        assertThat(resp.getStatus()).isEqualTo(PaymentStatus.COD_PENDING);
    }

    // ---- idempotency ----

    @Test
    void repeatedSuccessfulPaymentWithSameKeyReturnsExistingPaymentWithoutSavingAgain() {
        ProcessPaymentRequest req = cardReq("4242");
        Payment existing = succeededPayment(new BigDecimal("19.99"));
        existing.setIdempotencyKey("pay-key-1");
        when(repo.findByRequestedByUserIdAndIdempotencyKey(7L, "pay-key-1"))
                .thenReturn(Optional.of(existing));

        PaymentSvc.ProcessResult result = svc.process(req, 7L, "pay-key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().getId()).isEqualTo(1L);
        verify(repo, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void reusingSameKeyForDifferentOrderIsRejected() {
        ProcessPaymentRequest req = cardReq("4242");
        Payment existing = succeededPayment(new BigDecimal("19.99"));
        existing.setIdempotencyKey("pay-key-2");
        when(repo.findByRequestedByUserIdAndIdempotencyKey(7L, "pay-key-2"))
                .thenReturn(Optional.of(existing));

        req.setOrderId(999L);

        assertThatThrownBy(() -> svc.process(req, 7L, "pay-key-2"))
                .isInstanceOf(com.catalogix.payment.exception.IdempotencyConflictException.class);
        verify(repo, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void repeatedDeclinedPaymentWithSameKeyRemainsDeclined() {
        ProcessPaymentRequest req = cardReq("4242");
        Payment existing = new Payment(
                42L, 7L, new BigDecimal("19.99"),
                PaymentMethod.CARD, PaymentStatus.FAILED, null);
        existing.setId(2L);
        existing.setIdempotencyKey("pay-key-3");
        when(repo.findByRequestedByUserIdAndIdempotencyKey(7L, "pay-key-3"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> svc.process(req, 7L, "pay-key-3"))
                .isInstanceOf(DeclinedException.class);
        verify(repo, never()).saveAndFlush(any(Payment.class));
    }

    // ---- refund ----

    private Payment succeededPayment(BigDecimal amount) {
        Payment p = new Payment(42L, 7L, amount, PaymentMethod.CARD, PaymentStatus.SUCCEEDED, "MOCK-CARD-abc");
        p.setId(1L);
        return p;
    }

    private ProcessRefundRequest refundReq(String amount) {
        ProcessRefundRequest r = new ProcessRefundRequest();
        r.setOrderId(42L);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    @Test
    void refundSucceedsAgainstTheOriginalSuccessfulPayment() {
        when(repo.findByOrderIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(succeededPayment(new BigDecimal("100.00"))));
        when(refundRepo.findByOrderId(42L)).thenReturn(List.of());

        RefundResponse resp = svc.refund(refundReq("100.00"));

        assertThat(resp.getReference()).startsWith("MOCK-REFUND-");
        assertThat(resp.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void refundReplayWithTheSameIdempotencyKeyReturnsTheOriginalRefundInsteadOfRefundingAgain() {
        when(repo.findByOrderIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(succeededPayment(new BigDecimal("100.00"))));
        Refund earlier = new Refund(42L, 1L, new BigDecimal("100.00"), "MOCK-REFUND-first");
        earlier.setId(9L);
        when(refundRepo.findByOrderIdAndIdempotencyKey(42L, "cancel-order-42")).thenReturn(java.util.Optional.of(earlier));

        RefundResponse resp = svc.refund(refundReq("100.00"), "cancel-order-42");

        assertThat(resp.getReference()).isEqualTo("MOCK-REFUND-first");
        verify(refundRepo, never()).save(any(Refund.class));
    }

    @Test
    void refundRejectsReusingAnIdempotencyKeyWithADifferentAmount() {
        when(repo.findByOrderIdOrderByCreatedAtDesc(42L))
                .thenReturn(List.of(succeededPayment(new BigDecimal("100.00"))));
        Refund earlier = new Refund(42L, 1L, new BigDecimal("60.00"), "MOCK-REFUND-first");
        earlier.setId(9L);
        when(refundRepo.findByOrderIdAndIdempotencyKey(42L, "refund-key"))
                .thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> svc.refund(refundReq("50.00"), "refund-key"))
                .isInstanceOf(com.catalogix.payment.exception.IdempotencyConflictException.class);
        verify(refundRepo, never()).save(any(Refund.class));
    }

    @Test
    void refundWithAnIdempotencyKeyStoresTheKey() {
        when(repo.findByOrderIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(succeededPayment(new BigDecimal("100.00"))));
        when(refundRepo.findByOrderId(42L)).thenReturn(List.of());
        when(refundRepo.findByOrderIdAndIdempotencyKey(42L, "cancel-order-42")).thenReturn(java.util.Optional.empty());

        svc.refund(refundReq("100.00"), "cancel-order-42");

        verify(refundRepo).save(org.mockito.ArgumentMatchers.argThat(r -> "cancel-order-42".equals(r.getIdempotencyKey())));
    }

    @Test
    void refundThrowsWhenThereIsNoSuccessfulPaymentForTheOrder() {
        when(repo.findByOrderIdOrderByCreatedAtDesc(42L))
                .thenReturn(List.of());

        ProcessRefundRequest req = refundReq("100.00");

        assertThatThrownBy(() -> svc.refund(req))
                .isInstanceOf(NoSuchPaymentException.class);
    }

    @Test
    void refundIgnoresFailedPaymentAttemptsWhenFindingTheOriginal() {
        Payment declined = new Payment(
                42L,
                7L,
                new BigDecimal("100.00"),
                PaymentMethod.CARD,
                PaymentStatus.FAILED,
                null);

        when(repo.findByOrderIdOrderByCreatedAtDesc(42L))
                .thenReturn(List.of(declined));

        ProcessRefundRequest req = refundReq("100.00");

        assertThatThrownBy(() -> svc.refund(req))
                .isInstanceOf(NoSuchPaymentException.class);
    }

    @Test
    void refundRejectsAnAmountExceedingTheOriginalPayment() {
        when(repo.findByOrderIdOrderByCreatedAtDesc(42L)).thenReturn(
                List.of(succeededPayment(new BigDecimal("100.00")))
        );
        when(refundRepo.findByOrderId(42L)).thenReturn(List.of());

        ProcessRefundRequest req = refundReq("100.01");

        assertThatThrownBy(() -> svc.refund(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Added to close a real over-refund gap: two partial refunds whose sum
    // exceeds what was actually paid must be rejected, not just a single
    // refund request checked in isolation against the original amount.
    @Test
    void refundRejectsWhenPriorPartialRefundsWouldPushTheTotalOverTheOriginal() {
        when(repo.findByOrderIdOrderByCreatedAtDesc(42L)).thenReturn(
                List.of(succeededPayment(new BigDecimal("100.00")))
        );
        Refund priorRefund = new Refund(
                42L, 1L, new BigDecimal("60.00"), "MOCK-REFUND-prior"
        );
        when(refundRepo.findByOrderId(42L)).thenReturn(List.of(priorRefund));

        ProcessRefundRequest req = refundReq("40.01");

        assertThatThrownBy(() -> svc.refund(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refundAllowsPartialRefundsThatExactlySumToTheOriginal() {
        when(repo.findByOrderIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(succeededPayment(new BigDecimal("100.00"))));
        Refund priorRefund = new Refund(42L, 1L, new BigDecimal("60.00"), "MOCK-REFUND-prior");
        when(refundRepo.findByOrderId(42L)).thenReturn(List.of(priorRefund));

        RefundResponse resp = svc.refund(refundReq("40.00"));

        assertThat(resp.getAmount()).isEqualByComparingTo("40.00");
    }
}
