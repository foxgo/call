package com.callcenter.ingestion.outbox;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.common.mapper.CallEventOutboxMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRepositoryTest {

    @Test
    void shouldReturnEmptyClaimWithoutIssuingUpdate() {
        CallEventOutboxMapper mapper = mock(CallEventOutboxMapper.class);
        OutboxRepository repository = new OutboxRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 5, 20, 6, 0);

        when(mapper.selectPublishableIdsForClaim(now, 20, 10)).thenReturn(List.of());

        List<CallEventOutboxEntity> claimed = repository.claimPublishableBatch(now, 20, 10);

        assertThat(claimed).isEmpty();
        verify(mapper).selectPublishableIdsForClaim(now, 20, 10);
        verify(mapper, never()).update(eq(null), org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        verify(mapper, never()).selectClaimedBatchByIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldReturnEmptyClaimWhenConditionalUpdateClaimsNoRows() {
        CallEventOutboxMapper mapper = mock(CallEventOutboxMapper.class);
        OutboxRepository repository = new OutboxRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 5, 20, 6, 0);

        when(mapper.selectPublishableIdsForClaim(now, 20, 10)).thenReturn(List.of(1L, 2L));
        when(mapper.update(eq(null), org.mockito.ArgumentMatchers.any(UpdateWrapper.class))).thenReturn(0);

        List<CallEventOutboxEntity> claimed = repository.claimPublishableBatch(now, 20, 10);

        assertThat(claimed).isEmpty();
        verify(mapper).selectPublishableIdsForClaim(now, 20, 10);
        verify(mapper).update(eq(null), org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        verify(mapper, never()).selectClaimedBatchByIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDeleteOnlyProcessingRowsById() {
        CallEventOutboxMapper mapper = mock(CallEventOutboxMapper.class);
        OutboxRepository repository = new OutboxRepository(mapper);

        repository.deleteProcessingById(9L);

        ArgumentCaptor<QueryWrapper<CallEventOutboxEntity>> deleteCaptor =
                (ArgumentCaptor<QueryWrapper<CallEventOutboxEntity>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).delete(deleteCaptor.capture());
        QueryWrapper<CallEventOutboxEntity> delete = deleteCaptor.getValue();
        assertThat(delete.getSqlSegment()).contains("id", "status");
        assertThat(delete.getParamNameValuePairs()).containsValue(9L);
        assertThat(delete.getParamNameValuePairs()).containsValue(OutboxStatus.PROCESSING.name());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRequeueStaleProcessingRowsAsRetryableFailures() {
        CallEventOutboxMapper mapper = mock(CallEventOutboxMapper.class);
        OutboxRepository repository = new OutboxRepository(mapper);
        LocalDateTime staleBefore = LocalDateTime.of(2026, 5, 20, 5, 55);
        LocalDateTime requeuedAt = LocalDateTime.of(2026, 5, 20, 6, 0);

        repository.recoverStaleProcessingRows(staleBefore, requeuedAt);

        ArgumentCaptor<UpdateWrapper<CallEventOutboxEntity>> updateCaptor =
                (ArgumentCaptor<UpdateWrapper<CallEventOutboxEntity>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(eq(null), updateCaptor.capture());
        UpdateWrapper<CallEventOutboxEntity> update = updateCaptor.getValue();
        assertThat(update.getSqlSegment()).contains("status", "updated_at");
        assertThat(update.getSqlSet()).contains("status", "next_attempt_at", "updated_at");
        assertThat(update.getParamNameValuePairs()).containsValue(OutboxStatus.PROCESSING.name());
        assertThat(update.getParamNameValuePairs()).containsValue(OutboxStatus.FAILED.name());
        assertThat(update.getParamNameValuePairs()).containsValue(staleBefore);
        assertThat(update.getParamNameValuePairs()).containsValue(requeuedAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMarkFailedOnlyForProcessingRowsAndUseFailureTimeForUpdatedAt() {
        CallEventOutboxMapper mapper = mock(CallEventOutboxMapper.class);
        OutboxRepository repository = new OutboxRepository(mapper);
        LocalDateTime failedAt = LocalDateTime.of(2026, 5, 20, 6, 0);
        LocalDateTime nextAttemptAt = failedAt.plusSeconds(30);

        repository.markFailed(9L, 3, "java.lang.IllegalStateException: broker unavailable", failedAt, nextAttemptAt);

        ArgumentCaptor<UpdateWrapper<CallEventOutboxEntity>> updateCaptor =
                (ArgumentCaptor<UpdateWrapper<CallEventOutboxEntity>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(eq(null), updateCaptor.capture());
        UpdateWrapper<CallEventOutboxEntity> update = updateCaptor.getValue();
        assertThat(update.getSqlSegment()).contains("id", "status");
        assertThat(update.getSqlSet()).contains("status", "attempt_count", "last_error", "next_attempt_at", "updated_at");
        assertThat(update.getParamNameValuePairs()).containsValue(9L);
        assertThat(update.getParamNameValuePairs()).containsValue(OutboxStatus.PROCESSING.name());
        assertThat(update.getParamNameValuePairs()).containsValue(OutboxStatus.FAILED.name());
        assertThat(update.getParamNameValuePairs()).containsValue(3);
        assertThat(update.getParamNameValuePairs()).containsValue("java.lang.IllegalStateException: broker unavailable");
        assertThat(update.getParamNameValuePairs()).containsValue(nextAttemptAt);
        assertThat(update.getParamNameValuePairs()).containsValue(failedAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldConditionallyTransitionClaimedRowsToProcessingAndReloadThemFromDatabase() {
        CallEventOutboxMapper mapper = mock(CallEventOutboxMapper.class);
        OutboxRepository repository = new OutboxRepository(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 5, 20, 6, 0);
        CallEventOutboxEntity claimedFirst = event(1L, OutboxStatus.PROCESSING.name());
        claimedFirst.setUpdatedAt(now);
        CallEventOutboxEntity claimedSecond = event(2L, OutboxStatus.PROCESSING.name());
        claimedSecond.setUpdatedAt(now);

        when(mapper.selectPublishableIdsForClaim(now, 20, 10)).thenReturn(List.of(1L, 2L));
        when(mapper.update(eq(null), org.mockito.ArgumentMatchers.any(UpdateWrapper.class))).thenReturn(2);
        when(mapper.selectClaimedBatchByIds(List.of(1L, 2L))).thenReturn(List.of(claimedFirst, claimedSecond));

        List<CallEventOutboxEntity> claimed = repository.claimPublishableBatch(now, 20, 10);

        assertThat(claimed).containsExactly(claimedFirst, claimedSecond);
        assertThat(claimed)
                .allSatisfy(event -> {
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING.name());
                    assertThat(event.getUpdatedAt()).isEqualTo(now);
                });

        ArgumentCaptor<UpdateWrapper<CallEventOutboxEntity>> updateCaptor =
                (ArgumentCaptor<UpdateWrapper<CallEventOutboxEntity>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(eq(null), updateCaptor.capture());
        UpdateWrapper<CallEventOutboxEntity> update = updateCaptor.getValue();
        assertThat(update.getSqlSet()).contains("status", "updated_at");
        assertThat(update.getParamNameValuePairs()).containsValue(OutboxStatus.PROCESSING.name());
        assertThat(update.getSqlSegment()).contains("id", "status", "next_attempt_at", "attempt_count");
        assertThat(update.getParamNameValuePairs()).containsValue(OutboxStatus.NEW.name());
        assertThat(update.getParamNameValuePairs()).containsValue(OutboxStatus.FAILED.name());
        assertThat(update.getParamNameValuePairs()).containsValue(now);
        assertThat(update.getParamNameValuePairs()).containsValue(10);
        verify(mapper).selectClaimedBatchByIds(List.of(1L, 2L));
    }

    private static CallEventOutboxEntity event(Long id, String status) {
        CallEventOutboxEntity entity = new CallEventOutboxEntity();
        entity.setId(id);
        entity.setStatus(status);
        return entity;
    }
}
