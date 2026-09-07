package io.github.team404.tikitaka.performanceseat.search;

import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

// 공연 검색 문서. PostgreSQL이 source of truth이고 이 문서는 파생 뷰 — 동기화 대상 필드만 담는다
// (docs/tradeoffs/search/db-es-sync-strategy.md, #89). 좌석·가격·회차는 색인 대상 아님.
// createIndex=false: 인덱스 생성을 리포지토리 부트스트랩에 묶지 않는다 — ES가 없거나 nori 미설치여도
// 애플리케이션 컨텍스트는 떠야 하고, 인덱스는 PerformanceSearchReindexScheduler가 기동 후 만든다.
@Document(indexName = "performances", createIndex = false)
@Setting(settingPath = "elasticsearch/performance-settings.json")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceDocument {

    @Id
    private String id; // = performanceId (문자열)

    // 자유 텍스트 검색은 nori(형태소) 분석, 정확 일치/정렬/집계는 .keyword 서브필드
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "korean"),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
    private String title;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "korean"),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
    private String artist;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String venueName;

    // enum은 필터/집계 전용이라 keyword
    @Field(type = FieldType.Keyword)
    private String region;

    @Field(type = FieldType.Keyword)
    private String genre;

    @Field(type = FieldType.Keyword, index = false)
    private String posterUrl;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createdAt;

    @Builder
    private PerformanceDocument(String id, String title, String artist, String venueName,
            String region, String genre, String posterUrl, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.venueName = venueName;
        this.region = region;
        this.genre = genre;
        this.posterUrl = posterUrl;
        this.createdAt = createdAt;
    }

    public static PerformanceDocument from(Performance performance) {
        return PerformanceDocument.builder()
                .id(String.valueOf(performance.getId()))
                .title(performance.getTitle())
                .artist(performance.getArtist())
                .venueName(performance.getVenueName())
                .region(performance.getRegion().name())
                .genre(performance.getGenre().name())
                .posterUrl(performance.getPosterUrl())
                .createdAt(performance.getCreatedAt())
                .build();
    }

    // description은 색인 대상이 아니라 상세 조회로만 채워지므로 여기서는 null
    public PerformanceResponse toResponse() {
        return new PerformanceResponse(
                Long.valueOf(id),
                title,
                artist,
                venueName,
                PerformanceRegion.valueOf(region),
                PerformanceGenre.valueOf(genre),
                null,
                posterUrl,
                createdAt);
    }
}
