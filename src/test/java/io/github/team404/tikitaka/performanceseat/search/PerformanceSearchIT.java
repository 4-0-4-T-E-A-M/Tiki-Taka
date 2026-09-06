package io.github.team404.tikitaka.performanceseat.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// #90: nori 분석기가 적용된 인덱스에서 한국어 형태소 검색이 되는지 검증한다.
// docker/elasticsearch/Dockerfile을 그대로 빌드해 컨테이너를 띄우므로 별도 이미지 준비가 필요 없다.
@Testcontainers
@SpringBootTest(classes = PerformanceSearchIT.TestConfig.class)
class PerformanceSearchIT {

    static final ElasticsearchContainer elasticsearch = createContainer();

    private static ElasticsearchContainer createContainer() {
        String image = new ImageFromDockerfile("tikitaka-elasticsearch-it:8.18.8-nori", false)
                .withFileFromPath("Dockerfile", Path.of("docker/elasticsearch/Dockerfile"))
                .get();
        return new ElasticsearchContainer(DockerImageName.parse(image)
                .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        elasticsearch.start();
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @Autowired
    private PerformanceSearchRepository searchRepository;

    @Autowired
    private PerformanceSearchService searchService;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @BeforeEach
    void setUp() {
        var indexOps = elasticsearchOperations.indexOps(PerformanceDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();
    }

    @AfterEach
    void tearDown() {
        elasticsearchOperations.indexOps(PerformanceDocument.class).delete();
    }

    private void save(long id, String title, String artist, PerformanceGenre genre, PerformanceRegion region) {
        searchRepository.save(PerformanceDocument.builder()
                .id(String.valueOf(id))
                .title(title)
                .artist(artist)
                .venueName("올림픽공원 체조경기장")
                .genre(genre.name())
                .region(region.name())
                .posterUrl("http://example.com/" + id + ".jpg")
                .createdAt(LocalDateTime.now())
                .build());
        elasticsearchOperations.indexOps(PerformanceDocument.class).refresh();
    }

    @Test
    void 아티스트명_일부로_검색하면_해당_공연이_나온다() {
        save(1, "아이유 콘서트 2026", "아이유", PerformanceGenre.CONCERT, PerformanceRegion.SEOUL);
        save(2, "세븐틴 팬미팅", "세븐틴", PerformanceGenre.FAN_MEETING, PerformanceRegion.BUSAN);

        Page<PerformanceResponse> result = searchService.search("아이유", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(PerformanceResponse::id).containsExactly(1L);
    }

    @Test
    void nori_형태소_분석으로_조사가_붙어도_매칭된다() {
        save(1, "아이유의 콘서트", "아이유", PerformanceGenre.CONCERT, PerformanceRegion.SEOUL);

        // "아이유의" 가 형태소 분석으로 "아이유" + "의" 로 분리되어 색인된다
        Page<PerformanceResponse> result = searchService.search("아이유", null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void nori_decompound로_복합어의_일부로도_검색된다() {
        save(1, "뮤지컬 라이온킹", "라이온킹 컴퍼니", PerformanceGenre.MUSICAL, PerformanceRegion.SEOUL);

        // decompound_mode=mixed 라 "라이온킹" 이 "라이온" + "킹" 으로도 색인되어 부분어 검색 가능
        Page<PerformanceResponse> result = searchService.search("라이온", null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 장르_지역_필터가_키워드와_함께_적용된다() {
        save(1, "여름 뮤직 페스티벌", "여러 아티스트", PerformanceGenre.FESTIVAL, PerformanceRegion.SEOUL);
        save(2, "가을 뮤직 페스티벌", "여러 아티스트", PerformanceGenre.FESTIVAL, PerformanceRegion.BUSAN);
        save(3, "겨울 뮤지컬", "아무개", PerformanceGenre.MUSICAL, PerformanceRegion.SEOUL);

        Page<PerformanceResponse> result = searchService.search(
                "뮤직", PerformanceGenre.FESTIVAL, PerformanceRegion.SEOUL, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(PerformanceResponse::id).containsExactly(1L);
    }

    @Test
    void 키워드가_없으면_필터만으로_최신순_조회된다() {
        save(1, "공연 A", "아티스트", PerformanceGenre.CONCERT, PerformanceRegion.SEOUL);
        save(2, "공연 B", "아티스트", PerformanceGenre.CONCERT, PerformanceRegion.BUSAN);

        Page<PerformanceResponse> result = searchService.search(
                null, PerformanceGenre.CONCERT, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 결과가_없으면_빈_페이지를_반환한다() {
        save(1, "아이유 콘서트", "아이유", PerformanceGenre.CONCERT, PerformanceRegion.SEOUL);

        Page<PerformanceResponse> result = searchService.search("존재하지않는가수", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @EnableElasticsearchRepositories(basePackageClasses = PerformanceSearchRepository.class)
    @Import({
            ElasticsearchRestClientAutoConfiguration.class,
            ElasticsearchClientAutoConfiguration.class,
            ElasticsearchDataAutoConfiguration.class,
            PerformanceSearchService.class
    })
    static class TestConfig {
    }
}
