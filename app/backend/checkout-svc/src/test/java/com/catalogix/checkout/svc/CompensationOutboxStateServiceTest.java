package com.catalogix.checkout.svc;

import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.model.OutboxStatus;
import com.catalogix.checkout.repository.CompensationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompensationOutboxStateServiceTest {

    private CompensationOutboxRepository repository;
    private CompensationOutboxStateService service;

    @BeforeEach
    void setUp() {
        repository = mock(CompensationOutboxRepository.class);
        service = new CompensationOutboxStateService(repository);
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(CompensationOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void claimsIncrementAttemptsBeforeWorkStarts() {
        CompensationOutbox row = CompensationOutbox.releaseStock(5L, 1, "test", "release-5", "reserve-5");
        row.setId(1L);
        when(repository.claimPendingBatch(any(), any())).thenReturn(List.of(row));

        List<CompensationOutboxStateService.CompensationTask> tasks = service.claimPendingBatch();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().attempts()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void expiredLeaseAtRetryLimitIsDeadLetteredInsteadOfRetriedForever() {
        CompensationOutbox row = CompensationOutbox.releaseStock(5L, 1, "test");
        row.setId(2L);
        row.setStatus(OutboxStatus.PROCESSING);
        row.setAttempts(5);
        when(repository.claimPendingBatch(any(), any())).thenReturn(List.of(row));

        List<CompensationOutboxStateService.CompensationTask> tasks = service.claimPendingBatch();

        assertThat(tasks).isEmpty();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(row.getLastError()).contains("lease expired");
    }

    @Test
    void failureUsesAttemptAlreadyCountedAtClaimTime() {
        CompensationOutbox row = CompensationOutbox.releaseStock(5L, 1, "test");
        row.setId(3L);
        row.setAttempts(2);
        when(repository.findById(3L)).thenReturn(Optional.of(row));

        service.recordFailure(3L, "downstream is unavailable");

        assertThat(row.getAttempts()).isEqualTo(2);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getNextAttemptAt()).isNotNull();
    }
}
