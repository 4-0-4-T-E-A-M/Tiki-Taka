package io.github.team404.tikitaka.performanceseat.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PerformanceTest {

    @Test
    void 생성_시_필드가_그대로_반영된다() {
        // when
        Performance performance = performanceOf();

        // then
        assertThat(performance.getTitle()).isEqualTo("첫 콘서트");
        assertThat(performance.getArtist()).isEqualTo("아이유");
        assertThat(performance.getVenueName()).isEqualTo("올림픽공원 체조경기장");
        assertThat(performance.getRegion()).isEqualTo(PerformanceRegion.SEOUL);
        assertThat(performance.getGenre()).isEqualTo(PerformanceGenre.CONCERT);
        assertThat(performance.getCreatedAt()).isNotNull();
    }

    @Test
    void update_호출시_필드가_변경된다() {
        // given
        Performance performance = performanceOf();

        // when
        performance.update(
                "수정된 제목",
                "수정된 아티스트",
                "수정된 공연장",
                PerformanceRegion.BUSAN,
                PerformanceGenre.MUSICAL,
                "수정된 설명",
                "https://example.com/poster.png");

        // then
        assertThat(performance.getTitle()).isEqualTo("수정된 제목");
        assertThat(performance.getArtist()).isEqualTo("수정된 아티스트");
        assertThat(performance.getVenueName()).isEqualTo("수정된 공연장");
        assertThat(performance.getRegion()).isEqualTo(PerformanceRegion.BUSAN);
        assertThat(performance.getGenre()).isEqualTo(PerformanceGenre.MUSICAL);
        assertThat(performance.getDescription()).isEqualTo("수정된 설명");
        assertThat(performance.getPosterUrl()).isEqualTo("https://example.com/poster.png");
    }

    private Performance performanceOf() {
        return Performance.builder()
                .title("첫 콘서트")
                .artist("아이유")
                .venueName("올림픽공원 체조경기장")
                .region(PerformanceRegion.SEOUL)
                .genre(PerformanceGenre.CONCERT)
                .build();
    }
}
