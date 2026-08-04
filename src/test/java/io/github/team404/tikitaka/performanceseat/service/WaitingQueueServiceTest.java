package io.github.team404.tikitaka.performanceseat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.performanceseat.exception.QueueErrorCode;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.performanceseat.repository.WaitingQueueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WaitingQueueServiceTest {

    @Mock
    private WaitingQueueRepository waitingQueueRepository;

    @Mock
    private PerformanceScheduleRepository performanceScheduleRepository;

    @InjectMocks
    private WaitingQueueService waitingQueueService;

    @Test
    void 대기열에_진입하면_순번을_반환한다() {
        // given
        when(performanceScheduleRepository.existsById(1L)).thenReturn(true);
        when(waitingQueueRepository.rank(1L, 10L)).thenReturn(0L);

        // when
        long rank = waitingQueueService.enter(1L, 10L);

        // then
        assertThat(rank).isZero();
        verify(waitingQueueRepository).addIfAbsent(1L, 10L);
    }

    @Test
    void 존재하지_않는_회차면_예외가_발생한다() {
        // given
        when(performanceScheduleRepository.existsById(1L)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> waitingQueueService.enter(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(QueueErrorCode.SCHEDULE_NOT_FOUND);
        verify(waitingQueueRepository, never()).addIfAbsent(1L, 10L);
    }

    @Test
    void 대기열에_없는_사용자의_순번을_조회하면_예외가_발생한다() {
        // given
        when(waitingQueueRepository.rank(1L, 10L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> waitingQueueService.getRank(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND);
    }

    @Test
    void 순번이_허용_인원보다_작으면_입장이_허용된다() {
        // given
        when(waitingQueueRepository.rank(1L, 10L)).thenReturn(4L);

        // when & then
        assertThat(waitingQueueService.isAdmitted(1L, 10L, 5L)).isTrue();
        assertThat(waitingQueueService.isAdmitted(1L, 10L, 4L)).isFalse();
    }

    @Test
    void 대기_인원이_없으면_0을_반환한다() {
        // given
        when(waitingQueueRepository.size(1L)).thenReturn(null);

        // when & then
        assertThat(waitingQueueService.size(1L)).isZero();
    }
}
