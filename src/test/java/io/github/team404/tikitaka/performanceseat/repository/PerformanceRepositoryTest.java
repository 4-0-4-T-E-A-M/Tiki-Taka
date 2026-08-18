package io.github.team404.tikitaka.performanceseat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
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
class PerformanceRepositoryTest {

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private PerformanceScheduleRepository performanceScheduleRepository;

    private Performance concertSeoul;
    private Performance musicalSeoul;
    private Performance concertBusan;

    @BeforeEach
    void setUp() {
        concertSeoul = performanceRepository.save(performanceOf("콘서트 A", PerformanceGenre.CONCERT, PerformanceRegion.SEOUL));
        withCreatedAt(concertSeoul, LocalDateTime.now().minusDays(2));

        musicalSeoul = performanceRepository.save(performanceOf("뮤지컬 B", PerformanceGenre.MUSICAL, PerformanceRegion.SEOUL));
        withCreatedAt(musicalSeoul, LocalDateTime.now().minusDays(1));

        concertBusan = performanceRepository.save(performanceOf("콘서트 C", PerformanceGenre.CONCERT, PerformanceRegion.BUSAN));
        withCreatedAt(concertBusan, LocalDateTime.now());

        scheduleOf(concertSeoul.getId(), LocalDateTime.of(2026, 9, 1, 19, 0));
        scheduleOf(concertSeoul.getId(), LocalDateTime.of(2026, 9, 2, 19, 0));
        scheduleOf(musicalSeoul.getId(), LocalDateTime.of(2026, 10, 1, 19, 0));
        scheduleOf(concertBusan.getId(), LocalDateTime.of(2026, 9, 15, 19, 0));
    }

    @Test
    void 조건이_모두_없으면_전체_공연을_조회한다() {
        // given
        PerformanceSearchCondition condition = new PerformanceSearchCondition(null, null, null, null);

        // when
        Page<Performance> result = performanceRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 장르_조건으로_조회하면_해당_장르_공연만_반환한다() {
        // given
        PerformanceSearchCondition condition = new PerformanceSearchCondition(PerformanceGenre.CONCERT, null, null, null);

        // when
        Page<Performance> result = performanceRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).containsExactlyInAnyOrder(concertSeoul, concertBusan);
    }

    @Test
    void 지역_조건으로_조회하면_해당_지역_공연만_반환한다() {
        // given
        PerformanceSearchCondition condition = new PerformanceSearchCondition(null, PerformanceRegion.SEOUL, null, null);

        // when
        Page<Performance> result = performanceRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).containsExactlyInAnyOrder(concertSeoul, musicalSeoul);
    }

    @Test
    void 장르와_지역_조건을_조합하면_모든_조건을_만족하는_공연만_반환한다() {
        // given
        PerformanceSearchCondition condition =
                new PerformanceSearchCondition(PerformanceGenre.CONCERT, PerformanceRegion.SEOUL, null, null);

        // when
        Page<Performance> result = performanceRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).containsExactly(concertSeoul);
    }

    @Test
    void 날짜_기간_조건으로_조회하면_해당_기간에_회차가_있는_공연만_반환한다() {
        // given: 9월 상반기 -> concertSeoul(9/1, 9/2), concertBusan(9/15)는 포함되지 않음
        PerformanceSearchCondition condition = new PerformanceSearchCondition(
                null, null, LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 10, 0, 0));

        // when
        Page<Performance> result = performanceRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).containsExactly(concertSeoul);
    }

    @Test
    void 동일_공연에_회차가_여러_개여도_한_번만_반환된다() {
        // given: concertSeoul은 회차가 2개(9/1, 9/2)지만 결과는 한 건이어야 한다
        PerformanceSearchCondition condition = new PerformanceSearchCondition(
                null, null, LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 3, 0, 0));

        // when
        Page<Performance> result = performanceRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).containsExactly(concertSeoul);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 장르_지역_날짜_조건을_모두_조합해_조회한다() {
        // given
        PerformanceSearchCondition condition = new PerformanceSearchCondition(
                PerformanceGenre.CONCERT,
                PerformanceRegion.BUSAN,
                LocalDateTime.of(2026, 9, 10, 0, 0),
                LocalDateTime.of(2026, 9, 20, 0, 0));

        // when
        Page<Performance> result = performanceRepository.search(condition, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).containsExactly(concertBusan);
    }

    @Test
    void 결과는_생성순_내림차순으로_정렬되고_페이징된다() {
        // given
        PerformanceSearchCondition condition = new PerformanceSearchCondition(null, null, null, null);

        // when
        Page<Performance> result = performanceRepository.search(condition, PageRequest.of(0, 2));

        // then
        assertThat(result.getContent()).containsExactly(concertBusan, musicalSeoul);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    private Performance performanceOf(String title, PerformanceGenre genre, PerformanceRegion region) {
        return Performance.builder()
                .title(title)
                .artist("아티스트")
                .venueName("공연장")
                .region(region)
                .genre(genre)
                .build();
    }

    private void withCreatedAt(Performance performance, LocalDateTime createdAt) {
        ReflectionTestUtils.setField(performance, "createdAt", createdAt);
        performanceRepository.save(performance);
    }

    private void scheduleOf(Long performanceId, LocalDateTime performanceDatetime) {
        performanceScheduleRepository.save(PerformanceSchedule.builder()
                .performanceId(performanceId)
                .performanceDatetime(performanceDatetime)
                .build());
    }
}
