# Elasticsearch 로컬 환경

관련 이슈: #74 (Elasticsearch 환경 세팅)

이 문서는 **로컬에서 ES를 띄우고 애플리케이션이 연결되는지 확인**하는 것까지만 다룬다.
인덱스 매핑·nori 분석기·공연 데이터 색인·DB↔ES 동기화·검색 API는 6주차(#89·#90·#91·#93) 범위다.

## 버전

| 구성 | 버전 | 결정 근거 |
| --- | --- | --- |
| Elasticsearch 서버 | **8.18.8** (`docker.elastic.co/elasticsearch/elasticsearch:8.18.8`) | Spring Boot 3.5.16이 관리하는 `co.elastic.clients:elasticsearch-java` 버전(8.18.8)과 major/minor를 맞춤 |
| 연동 방식 | **Spring Data Elasticsearch** (`spring-boot-starter-data-elasticsearch`) | 프로젝트가 이미 Spring Data JPA/Redis 저장소 추상화를 쓰고 있어 일관됨. 세밀한 검색 DSL이 필요할 때는 이 스타터가 함께 올려주는 `co.elastic.clients.elasticsearch.ElasticsearchClient` 빈을 그대로 주입해 native 쿼리를 쓸 수 있어, 지금 단계에서 공식 Java Client를 직접 조립할 이유는 없다. (최종 패키지 구조는 #90 구현자 판단 — `docs/tradeoffs/search/queryDSL-vs-es-role-split.md`) |

`analysis-nori` 플러그인은 아직 이미지에 넣지 않았다 — nori 적용은 #90이며, 그때 커스텀 이미지(build context)로 플러그인을 추가한다.

## 실행

```bash
docker compose up -d elasticsearch      # 또는 docker compose up -d 로 전체
```

로컬 개발 전용 설정 (운영 클러스터 구성 아님):
- `discovery.type=single-node` — 단일 노드
- `xpack.security.enabled=false` — 인증 없이 `http://localhost:9200` 접근
- `ES_JAVA_OPTS=-Xms512m -Xmx512m` — postgres/redis/kafka와 함께 뜨므로 힙 제한
- 데이터는 `elasticsearch_data` 볼륨에 유지

## 연결 확인

```bash
# 1. 컨테이너 헬스체크 (docker-compose가 _cluster/health를 폴링)
docker compose ps elasticsearch          # STATUS가 healthy

# 2. 직접 호출
curl http://localhost:9200/_cluster/health?pretty      # "status" : "green" 또는 "yellow"
curl http://localhost:9200                             # 버전 정보(8.18.8)

# 3. 애플리케이션에서
./gradlew bootRun    # .env에 ELASTICSEARCH_URIS=http://localhost:9200 필요
```

## 애플리케이션 설정

- `.env` : `ELASTICSEARCH_URIS=http://localhost:9200`
- `application.yaml` : `spring.elasticsearch.uris` / `connection-timeout` / `socket-timeout`
- 별도 `@Configuration`은 없다 — Spring Boot 오토컨피그가 `spring.elasticsearch.*`로
  `RestClient` → `ElasticsearchClient` → `ElasticsearchOperations`를 구성한다.
- `@EnableElasticsearchRepositories`는 ES 도큐먼트/리포지토리가 생기는 #90에서 필요해지면 추가한다.

## 테스트

`ElasticsearchConnectionIT` — Testcontainers로 `elasticsearch:8.18.8`를 띄우고,
애플리케이션 설정으로 구성된 `ElasticsearchClient`가 클러스터에 연결되어 `cluster().health()` /
`ping()`이 성공하는지 검증한다. (Docker 필요)
