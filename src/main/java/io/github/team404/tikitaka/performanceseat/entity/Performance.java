package io.github.team404.tikitaka.performanceseat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "performances")
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
