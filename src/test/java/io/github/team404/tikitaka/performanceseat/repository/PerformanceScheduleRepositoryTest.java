package io.github.team404.tikitaka.performanceseat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class PerformanceScheduleRepositoryTest {

    @Autowired
    private PerformanceScheduleRepository performanceScheduleRepository;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        // DB timestamp 정밀도(마이크로초) 반올림으로 나노초 단위 경계 비교가 흔들리지 않도록 밀리초로 맞춘다.
        now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }

    @Test
    void 오픈_시각이_지났고_아직_SCHEDULED인_회차만_조회한다() {
        // given
        PerformanceSchedule due = save(now.minusMinutes(1), ScheduleStatus.SCHEDULED);
        save(now.plusMinutes(1), ScheduleStatus.SCHEDULED);              // 오픈 시각 전
        save(now.minusMinutes(1), ScheduleStatus.OPEN);                  // 이미 열림
        save(now.minusMinutes(1), ScheduleStatus.CANCELED);             // 취소됨

        // when
        List<PerformanceSchedule> result = performanceScheduleRepository
                .findAllByStatusAndOpenAtLessThanEqual(ScheduleStatus.SCHEDULED, now);

        // then
        assertThat(result).extracting(PerformanceSchedule::getId).containsExactly(due.getId());
    }

    @Test
    void 오픈_시각이_정확히_현재_시각과_같아도_포함한다() {
        // given
        PerformanceSchedule exact = save(now, ScheduleStatus.SCHEDULED);

        // when
        List<PerformanceSchedule> result = performanceScheduleRepository
                .findAllByStatusAndOpenAtLessThanEqual(ScheduleStatus.SCHEDULED, now);

        // then
        assertThat(result).extracting(PerformanceSchedule::getId).containsExactly(exact.getId());
    }

    @Test
    void openIfScheduled은_SCHEDULED_상태일_때만_전환하고_영향받은_행_수를_반환한다() {
        // given
        PerformanceSchedule schedule = save(now.minusMinutes(1), ScheduleStatus.SCHEDULED);

        // when — 같은 행에 두 번 호출(동시 호출을 순차로 흉내)
        int firstAffected = performanceScheduleRepository.openIfScheduled(schedule.getId());
        int secondAffected = performanceScheduleRepository.openIfScheduled(schedule.getId());

        // then — 최초 1건만 영향받고, 두번째 호출은 조건(status = SCHEDULED)이 더 이상 맞지 않아 0건
        assertThat(firstAffected).isEqualTo(1);
        assertThat(secondAffected).isZero();
        assertThat(performanceScheduleRepository.findById(schedule.getId()).orElseThrow().getStatus())
                .isEqualTo(ScheduleStatus.OPEN);
    }

    @Test
    void openIfScheduled은_SCHEDULED가_아닌_회차에는_적용되지_않는다() {
        // given
        PerformanceSchedule alreadyOpen = save(now.minusMinutes(1), ScheduleStatus.OPEN);

        // when
        int affected = performanceScheduleRepository.openIfScheduled(alreadyOpen.getId());

        // then
        assertThat(affected).isZero();
    }

    private PerformanceSchedule save(LocalDateTime openAt, ScheduleStatus status) {
        PerformanceSchedule schedule = PerformanceSchedule.builder()
                .performanceId(1L)
                .performanceDatetime(openAt.plusDays(7))
                .openAt(openAt)
                .build();
        ReflectionTestUtils.setField(schedule, "status", status);
        return performanceScheduleRepository.save(schedule);
    }
}
