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
    void 이미_통과한_사용자는_다시_큐에_넣지_않고_현재_상태를_반환한다() {
        // given
        when(performanceScheduleRepository.existsById(1L)).thenReturn(true);
        when(waitingQueueRepository.isAdmitted(1L, 10L)).thenReturn(true);
        when(waitingQueueRepository.rank(1L, 10L)).thenReturn(null);

        // when
        long rank = waitingQueueService.enter(1L, 10L);

        // then — 재진입해도 큐 뒤로 밀리지 않는다
        assertThat(rank).isZero();
        verify(waitingQueueRepository, never()).addIfAbsent(1L, 10L);
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
    void 대기열에도_통과_집합에도_없는_사용자의_순번을_조회하면_예외가_발생한다() {
        // given
        when(waitingQueueRepository.rank(1L, 10L)).thenReturn(null);
        when(waitingQueueRepository.isAdmitted(1L, 10L)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> waitingQueueService.getRank(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND);
    }

    @Test
    void 통과한_사용자의_순번은_0으로_취급한다() {
        // given — 대기열 ZSET에서 빠졌지만 admitted 집합에는 있음
        when(waitingQueueRepository.rank(1L, 10L)).thenReturn(null);
        when(waitingQueueRepository.isAdmitted(1L, 10L)).thenReturn(true);

        // when & then
        assertThat(waitingQueueService.getRank(1L, 10L)).isZero();
    }

    @Test
    void 입장_허용_여부는_admitted_집합_소속으로_판단한다() {
        // given
        when(waitingQueueRepository.isAdmitted(1L, 10L)).thenReturn(true);

        // when & then
        assertThat(waitingQueueService.isAdmitted(1L, 10L)).isTrue();
    }

    @Test
    void 대기_인원이_없으면_0을_반환한다() {
        // given
        when(waitingQueueRepository.size(1L)).thenReturn(null);

        // when & then
        assertThat(waitingQueueService.size(1L)).isZero();
    }

    @Test
    void 입장_허용_목표가_설정되지_않았으면_아무도_통과시키지_않는다() {
        // given — 오픈 전: admit-count 키 없음 → 0
        when(waitingQueueRepository.admitCount(1L)).thenReturn(0L);

        // when
        int admitted = waitingQueueService.admitDueUsers(1L);

        // then
        assertThat(admitted).isZero();
        verify(waitingQueueRepository, never()).admitFront(1L, 0L);
    }

    @Test
    void 목표에_모자란_만큼만_대기열_앞에서_통과시킨다() {
        // given — 목표 100명, 이미 30명 통과 → 70명분 통과 시도
        when(waitingQueueRepository.admitCount(1L)).thenReturn(100L);
        when(waitingQueueRepository.admittedSize(1L)).thenReturn(30L);
        when(waitingQueueRepository.admitFront(1L, 70L)).thenReturn(70);

        // when
        int admitted = waitingQueueService.admitDueUsers(1L);

        // then
        assertThat(admitted).isEqualTo(70);
        verify(waitingQueueRepository).admitFront(1L, 70L);
    }

    @Test
    void 이미_목표만큼_통과했으면_더_통과시키지_않는다() {
        // given
        when(waitingQueueRepository.admitCount(1L)).thenReturn(100L);
        when(waitingQueueRepository.admittedSize(1L)).thenReturn(100L);

        // when
        int admitted = waitingQueueService.admitDueUsers(1L);

        // then
        assertThat(admitted).isZero();
        verify(waitingQueueRepository, never()).admitFront(1L, 0L);
    }
}
