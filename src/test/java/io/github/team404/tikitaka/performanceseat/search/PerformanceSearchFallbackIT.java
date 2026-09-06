package io.github.team404.tikitaka.performanceseat.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import io.github.team404.tikitaka.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// #93: Elasticsearch가 응답하지 않을 때 검색 API가 500이 아니라 PostgreSQL 폴백으로 200을 주고
// degraded=true 를 표시하는지 엔드투엔드로 검증한다. ES는 실제로 닿을 수 없는 주소를 쓴다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PerformanceSearchFallbackIT {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // 닿을 수 없는 ES 주소 — 검색 시 즉시 connection refused → 폴백 경로가 타야 한다
        registry.add("spring.elasticsearch.uris", () -> "http://localhost:1");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PerformanceRepository performanceRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        performanceRepository.deleteAll();
    }

    private void save(String title, String artist, PerformanceGenre genre, PerformanceRegion region) {
        performanceRepository.save(Performance.builder()
                .title(title)
                .artist(artist)
                .venueName("체조경기장")
                .region(region)
                .genre(genre)
                .description("설명")
                .posterUrl("http://example.com/p.jpg")
                .build());
    }

    private HttpEntity<Void> authorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtTokenProvider.generateAccessToken(1L, UserRole.USER));
        return new HttpEntity<>(headers);
    }

    @Test
    void ES_장애시_PostgreSQL_부분일치로_폴백하고_degraded_true를_반환한다() {
        save("아이유 콘서트 2026", "아이유", PerformanceGenre.CONCERT, PerformanceRegion.SEOUL);
        save("세븐틴 팬미팅", "세븐틴", PerformanceGenre.FAN_MEETING, PerformanceRegion.BUSAN);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/performances/search?q=아이유", HttpMethod.GET, authorized(), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = response.getBody().get("data");
        assertThat(data.get("degraded").asBoolean()).isTrue();
        assertThat(data.get("totalElements").asInt()).isEqualTo(1);
        assertThat(data.get("content").get(0).get("title").asText()).isEqualTo("아이유 콘서트 2026");
    }

    @Test
    void 폴백_상태에서도_genre_region_필터와_페이지네이션_계약을_지킨다() {
        save("여름 뮤지컬 페스티벌", "컴퍼니 A", PerformanceGenre.MUSICAL, PerformanceRegion.SEOUL);
        save("가을 뮤지컬 페스티벌", "컴퍼니 B", PerformanceGenre.MUSICAL, PerformanceRegion.BUSAN);
        save("겨울 콘서트", "컴퍼니 C", PerformanceGenre.CONCERT, PerformanceRegion.SEOUL);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/performances/search?q=뮤지컬&genre=MUSICAL&region=SEOUL&page=0&size=10",
                HttpMethod.GET, authorized(), JsonNode.class);

        JsonNode data = response.getBody().get("data");
        assertThat(data.get("degraded").asBoolean()).isTrue();
        assertThat(data.get("totalElements").asInt()).isEqualTo(1);
        assertThat(data.get("page").asInt()).isZero();
        assertThat(data.get("size").asInt()).isEqualTo(10);
        assertThat(data.get("content").get(0).get("title").asText()).isEqualTo("여름 뮤지컬 페스티벌");
    }

    @Test
    void 폴백_상태에서_결과가_없으면_빈_결과를_준다() {
        save("아이유 콘서트", "아이유", PerformanceGenre.CONCERT, PerformanceRegion.SEOUL);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/performances/search?q=존재하지않는키워드", HttpMethod.GET, authorized(), JsonNode.class);

        JsonNode data = response.getBody().get("data");
        assertThat(data.get("degraded").asBoolean()).isTrue();
        assertThat(data.get("totalElements").asInt()).isZero();
        assertThat(data.get("content")).isEmpty();
    }
}
