package com.catalogix.checkout.svc;

import com.catalogix.checkout.model.OrderEventOutbox;
import com.catalogix.checkout.model.OrderEventOutboxStatus;
import com.catalogix.checkout.repository.OrderEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderEventOutboxStateServiceTest {

    private OrderEventOutboxRepository repository;
    private OrderEventOutboxStateService service;

    @BeforeEach
    void setUp() {
        repository = mock(OrderEventOutboxRepository.class);
        service = new OrderEventOutboxStateService(repository);
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(OrderEventOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void claimCountsTheAttemptBeforeBrokerIo() {
        OrderEventOutbox row = new OrderEventOutbox("order.confirmed", "{}");
        row.setId(1L);
        when(repository.claimDueBatch(any(), any())).thenReturn(List.of(row));

        List<OrderEventOutboxStateService.OrderEventTask> tasks = service.claimDueBatch();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().attempts()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(OrderEventOutboxStatus.PROCESSING);
    }

    @Test
    void expiredLeaseAfterRetryLimitMovesEventToDeadLetter() {
        OrderEventOutbox row = new OrderEventOutbox("order.confirmed", "{}");
        row.setId(2L);
        row.setStatus(OrderEventOutboxStatus.PROCESSING);
        row.setAttempts(8);
        when(repository.claimDueBatch(any(), any())).thenReturn(List.of(row));

        assertThat(service.claimDueBatch()).isEmpty();
        assertThat(row.getStatus()).isEqualTo(OrderEventOutboxStatus.DEAD_LETTER);
        assertThat(row.getLastError()).contains("lease expired");
    }

    @Test
    void publisherFailureDoesNotDoubleCountAlreadyClaimedAttempt() {
        OrderEventOutbox row = new OrderEventOutbox("order.confirmed", "{}");
        row.setId(3L);
        row.setAttempts(3);
        when(repository.findById(3L)).thenReturn(Optional.of(row));

        service.recordFailure(3L, "broker unavailable");

        assertThat(row.getAttempts()).isEqualTo(3);
        assertThat(row.getStatus()).isEqualTo(OrderEventOutboxStatus.PENDING);
        assertThat(row.getNextAttemptAt()).isNotNull();
    }
}
