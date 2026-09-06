package io.github.team404.tikitaka.performanceseat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PerformanceScheduleTest {

    private PerformanceSchedule scheduleWith(ScheduleStatus status) {
        PerformanceSchedule schedule = PerformanceSchedule.builder()
                .performanceId(1L)
                .performanceDatetime(LocalDateTime.now().plusDays(7))
                .openAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(schedule, "status", status);
        return schedule;
    }

    @Test
    void 생성_직후에는_SCHEDULED이고_예매_불가다() {
        PerformanceSchedule schedule = scheduleWith(ScheduleStatus.SCHEDULED);

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
        assertThat(schedule.isBookable()).isFalse();
    }

    @Test
    void SCHEDULED_회차를_열면_OPEN으로_전환되고_true를_반환한다() {
        PerformanceSchedule schedule = scheduleWith(ScheduleStatus.SCHEDULED);

        boolean opened = schedule.open();

        assertThat(opened).isTrue();
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.OPEN);
        assertThat(schedule.isBookable()).isTrue();
    }

    @Test
    void 이미_OPEN인_회차를_다시_열면_아무_변화_없이_false를_반환한다() {
        PerformanceSchedule schedule = scheduleWith(ScheduleStatus.OPEN);

        boolean opened = schedule.open();

        assertThat(opened).isFalse();
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.OPEN);
    }

    @Test
    void CLOSED나_CANCELED_회차는_다시_열_수_없다() {
        assertThatThrownBy(() -> scheduleWith(ScheduleStatus.CLOSED).open())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> scheduleWith(ScheduleStatus.CANCELED).open())
                .isInstanceOf(IllegalStateException.class);
    }
}
