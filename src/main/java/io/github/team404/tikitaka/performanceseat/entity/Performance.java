package io.github.team404.tikitaka.performanceseat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// genre 단일 + (region, genre) 복합 인덱스는 #72 EXPLAIN 분석과 #73 인덱스 설계 결정에서 실측 검증됨
// (docs/perf/performance-search-explain-analysis.md, docs/tradeoffs/database/performance-search-index-design.md).
// docs/db/ddl.sql에도 인덱스가 문서화돼 있었지만 여기 선언이 없어 ddl-auto=update가 실제로는
// 만들지 않고 있었음(PK만 존재) — 이 애노테이션이 그 갭을 메운다.
// #73 결정: performances(region) 단일 인덱스는 (region, genre) 복합이 접두사 스캔으로 대체하므로 두지 않는다.
@Entity
@Table(name = "performances", indexes = {
        @Index(name = "idx_performances_genre", columnList = "genre"),
        @Index(name = "idx_performances_region_genre", columnList = "region, genre")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id")
    private Long id; // 공연 PK

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "artist", nullable = false)
    private String artist;

    @Column(name = "venue_name", nullable = false)
    private String venueName;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false)
    private PerformanceRegion region;

    @Enumerated(EnumType.STRING)
    @Column(name = "genre", nullable = false)
    private PerformanceGenre genre; // 날짜·장르·지역 복합 검색(5주차)에 쓰이는 필드

    @Column(name = "description")
    private String description;

    @Column(name = "poster_url")
    private String posterUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Performance(String title, String artist, String venueName, PerformanceRegion region,
            PerformanceGenre genre, String description, String posterUrl) {
        this.title = title;
        this.artist = artist;
        this.venueName = venueName;
        this.region = region;
        this.genre = genre;
        this.description = description;
        this.posterUrl = posterUrl;
        this.createdAt = LocalDateTime.now();
    }

    // 공연 기본 정보 수정 (회차/구역/좌석 구조는 별도 API 소관)
    public void update(String title, String artist, String venueName, PerformanceRegion region,
            PerformanceGenre genre, String description, String posterUrl) {
        this.title = title;
        this.artist = artist;
        this.venueName = venueName;
        this.region = region;
        this.genre = genre;
        this.description = description;
        this.posterUrl = posterUrl;
    }
}
