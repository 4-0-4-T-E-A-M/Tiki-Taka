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
