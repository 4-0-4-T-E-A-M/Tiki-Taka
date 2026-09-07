package io.github.team404.tikitaka.global.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// #74: Elasticsearch 환경·연결만 확인한다. 인덱스 매핑·nori·색인은 6주차(#90).
@Testcontainers
@SpringBootTest(classes = ElasticsearchConnectionIT.TestConfig.class)
class ElasticsearchConnectionIT {

    // docker-compose.yml과 동일한 8.18.x — 관리되는 elasticsearch-java 클라이언트 버전과 맞춤
    @Container
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.18.8"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    void 애플리케이션_설정으로_구성된_클라이언트가_클러스터에_연결된다() throws IOException {
        HealthResponse health = elasticsearchClient.cluster().health();

        assertThat(health.status()).isIn(HealthStatus.Green, HealthStatus.Yellow);
        assertThat(health.numberOfNodes()).isEqualTo(1);
    }

    @Test
    void ping이_성공한다() throws IOException {
        assertThat(elasticsearchClient.ping().value()).isTrue();
    }

    @Import({ElasticsearchRestClientAutoConfiguration.class, ElasticsearchClientAutoConfiguration.class})
    static class TestConfig {
    }
}
