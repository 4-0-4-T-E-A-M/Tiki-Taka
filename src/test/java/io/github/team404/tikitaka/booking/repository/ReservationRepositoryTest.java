package io.github.team404.tikitaka.booking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.booking.entity.Reservation;
import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository reservationRepository;

    private Reservation userAConfirmed;
    private Reservation userAPending;
    private Reservation userBConfirmed;

    @BeforeEach
    void setUp() {
        userAConfirmed = reservationOf(1L, 10L, 100L);
        userAConfirmed.confirm();
        withCreatedAt(userAConfirmed, LocalDateTime.now().minusDays(5));

        userAPending = reservationOf(1L, 10L, 200L);
        withCreatedAt(userAPending, LocalDateTime.now().minusDays(1));

        userBConfirmed = reservationOf(2L, 20L, 100L);
        userBConfirmed.confirm();
        withCreatedAt(userBConfirmed, LocalDateTime.now());

        reservationRepository.saveAll(List.of(userAConfirmed, userAPending, userBConfirmed));
    }

    @Test
    void 조건이_모두_없으면_전체_예매를_조회한다() {
        // given
        ReservationSearchCondition condition = new ReservationSearchCondition(null, null, null, null, null, null);

        // when
        Page<Reservation> result = reservationRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 단일_조건_userId로_조회하면_해당_유저_예매만_반환한다() {
        // given
        ReservationSearchCondition condition = new ReservationSearchCondition(1L, null, null, null, null, null);

        // when
        Page<Reservation> result = reservationRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent())
                .extracting(Reservation::getUserId)
                .containsOnly(1L);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 복수_조건을_조합하면_모든_조건을_만족하는_예매만_반환한다() {
        // given: userId=1 AND status=CONFIRMED -> userAConfirmed만 해당
        ReservationSearchCondition condition =
                new ReservationSearchCondition(1L, null, null, ReservationStatus.CONFIRMED, null, null);

        // when
        Page<Reservation> result = reservationRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).containsExactly(userAConfirmed);
    }

    @Test
    void scheduleId와_상태_조건을_조합해_조회한다() {
        // given: scheduleId=100 AND status=CONFIRMED -> userAConfirmed, userBConfirmed
        ReservationSearchCondition condition =
                new ReservationSearchCondition(null, null, 100L, ReservationStatus.CONFIRMED, null, null);

        // when
        Page<Reservation> result = reservationRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).containsExactlyInAnyOrder(userAConfirmed, userBConfirmed);
    }

    @Test
    void 생성시각_기간_조건으로_조회한다() {
        // given: 최근 2일 이내 -> userAPending, userBConfirmed
        ReservationSearchCondition condition = new ReservationSearchCondition(
                null, null, null, null, LocalDateTime.now().minusDays(2), LocalDateTime.now().plusMinutes(1));

        // when
        Page<Reservation> result = reservationRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).containsExactlyInAnyOrder(userAPending, userBConfirmed);
    }

    @Test
    void 결과는_생성시각_내림차순으로_정렬되고_페이징된다() {
        // given
        ReservationSearchCondition condition = new ReservationSearchCondition(null, null, null, null, null, null);

        // when
        Page<Reservation> result = reservationRepository.search(condition, PageRequest.of(0, 2));

        // then
        assertThat(result.getContent()).containsExactly(userBConfirmed, userAPending);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    private Reservation reservationOf(Long userId, Long simulationId, Long scheduleId) {
        return Reservation.builder()
                .userId(userId)
                .simulationId(simulationId)
                .scheduleId(scheduleId)
                .selectedQuantity(1)
                .build();
    }

    private void withCreatedAt(Reservation reservation, LocalDateTime createdAt) {
        ReflectionTestUtils.setField(reservation, "createdAt", createdAt);
    }
}