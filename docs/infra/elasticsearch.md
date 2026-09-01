# Elasticsearch 로컬 환경

관련 이슈: #74 (환경 세팅), #90 (공연 검색 인덱싱 + nori)

로컬 ES 실행/연결 확인(#74)과, 공연 검색 인덱스·nori 분석기·재색인 절차(#90)를 다룬다.
DB↔ES 동기화 전략은 `docs/tradeoffs/search/db-es-sync-strategy.md`(#89), ES 선택 이유는 #91.

## 버전

| 구성 | 버전 | 결정 근거 |
| --- | --- | --- |
| Elasticsearch 서버 | **8.18.8** (`docker.elastic.co/elasticsearch/elasticsearch:8.18.8`) | Spring Boot 3.5.16이 관리하는 `co.elastic.clients:elasticsearch-java` 버전(8.18.8)과 major/minor를 맞춤 |
| 연동 방식 | **Spring Data Elasticsearch** (`spring-boot-starter-data-elasticsearch`) | 프로젝트가 이미 Spring Data JPA/Redis 저장소 추상화를 쓰고 있어 일관됨. 세밀한 검색 DSL이 필요할 때는 이 스타터가 함께 올려주는 `co.elastic.clients.elasticsearch.ElasticsearchClient` 빈을 그대로 주입해 native 쿼리를 쓸 수 있어, 지금 단계에서 공식 Java Client를 직접 조립할 이유는 없다. (최종 패키지 구조는 #90 구현자 판단 — `docs/tradeoffs/search/queryDSL-vs-es-role-split.md`) |

`analysis-nori`(한국어 형태소 분석) 플러그인을 포함한 커스텀 이미지를 쓴다 —
`docker/elasticsearch/Dockerfile` (`FROM ...8.18.8` + `elasticsearch-plugin install analysis-nori`).

## 실행

```bash
docker compose up -d --build elasticsearch   # 최초 1회 또는 Dockerfile 변경 시 --build
docker compose up -d                          # 이후 전체 기동
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
- `application.yaml` : `spring.elasticsearch.uris` / `connection-timeout` / `socket-timeout`,
  `performance.search.reindex-cron` (기본 매일 04:00)
- 별도 `@Configuration`은 없다 — Spring Boot 오토컨피그가 `spring.elasticsearch.*`로
  `RestClient` → `ElasticsearchClient` → `ElasticsearchOperations`를 구성하고,
  `@EnableElasticsearchRepositories`도 스타터가 자동 활성화한다.

# 공연 검색 인덱스 (#90)

## 문서·매핑

- 인덱스: `performances` — 도큐먼트는 `PerformanceDocument` (`performanceseat/search`)
- 색인 대상 필드: `title`, `artist`, `venueName`, `region`, `genre`, `posterUrl`, `createdAt`
  — PostgreSQL이 source of truth이고 이 문서는 파생 뷰. 좌석·가격·회차·`description`은 색인 안 함
- settings: `src/main/resources/elasticsearch/performance-settings.json` (`@Setting`으로 로드)
  - `korean` analyzer = `nori_tokenizer`(decompound_mode=mixed) + `nori_part_of_speech` + `lowercase`
  - `title`/`artist`는 `text`(korean) + `.keyword` 멀티필드 — 형태소 검색과 정확 일치/정렬 모두 대응

## 동기화 (db-es-sync-strategy.md, #89)

- **평상시**: `PerformanceService` create/update/delete → 커밋 후 `PerformanceChangedEvent`
  (`performanceId` + `UPSERT`/`DELETE`) 를 Kafka `performance-events` 토픽에 발행 →
  `PerformanceChangedEventConsumer`가 UPSERT면 PostgreSQL 재조회 후 문서 전체 upsert, DELETE면 id로 삭제.
  재조회 방식이라 중복 수신·순서 역전에 멱등.
- **안전망**: `PerformanceSearchReindexScheduler`가 매일 04:00 (`performance.search.reindex-cron`)
  전체 재색인 → 이벤트 유실로 생긴 드리프트를 원상 복구.

## 재색인 / 인덱스 재생성

```java
performanceSearchIndexer.reindexAll();   // 인덱스 삭제 → 매핑과 함께 재생성 → PostgreSQL 전체 bulk 색인
```

- 현재 구현은 **재생성 중 짧은 검색 공백**이 생긴다 (연습용 규모라 감수).
- 운영 무중단 절차(참고): `performances-v2` 새 인덱스에 색인 완료 → `performances` alias를
  v1→v2로 원자적 전환 (`_aliases` actions) → v1 삭제. 매핑을 바꾸는 재색인도 이 순서를 따른다.

## 테스트

- `ElasticsearchConnectionIT` (#74) — 앱 설정 기반 `ElasticsearchClient` 연결/health/ping
- `PerformanceSearchIT` (#90) — `docker/elasticsearch/Dockerfile`을 빌드해 nori 포함 컨테이너로
  한국어 형태소 검색(조사 분리, decompound 부분어), genre/region 필터, 무결과 케이스 검증
- `PerformanceSearchIndexerTest` / `PerformanceChangedEventConsumerTest` — 색인·소비 로직 단위 테스트

(모두 Docker 필요)
