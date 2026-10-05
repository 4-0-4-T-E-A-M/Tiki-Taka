# 공연 검색(Elasticsearch) 응답시간 — 병목 분석과 개선

Issue #116. #90/#93에서 구현한 `PerformanceSearchService`(ES 기본 경로 + PostgreSQL 폴백)의
응답 성능 기준선을 잡고, 병목 후보를 점검한 뒤 실제로 적용한 개선을 정리한다.

## 이 문서가 다루지 않는 것

이름이 비슷한 `docs/perf/performance-search-explain-analysis.md`(#72)는 **QueryDSL/PostgreSQL**
경로(`PerformanceRepository.search()`)의 `EXPLAIN ANALYZE` 분석이다. 이 문서는 **Elasticsearch**
경로(`PerformanceSearchService.searchViaElasticsearch()`)만 다룬다 — 두 경로는
`docs/tradeoffs/search/queryDSL-vs-es-role-split.md`(#93)가 정리한 대로 서로 다른 조회 패턴을
맡고 있어 병목 지점도 다르다.

## 측정 환경 — 이번 작업에서는 실제 측정을 못 했다

이 작업을 진행한 환경에 Docker가 없어 로컬 Elasticsearch(nori 포함 이미지, `docker compose up -d`)를
띄울 수 없었다. 그래서 **아래 "결과" 수치는 전부 비워두고 "테스트 예정"으로 표시**했다 —
CLAUDE.md 지침("실측 결과가 없으면 수치를 지어내지 않고 테스트 예정으로 표시")을 그대로 따른다.

대신 다음 두 가지는 이번에 끝냈다.

1. **병목 후보 분석** — 코드·매핑·인덱스 설정을 직접 읽고 무엇이 이미 괜찮고 무엇이 의심되는지 판단 (실측 없이도 가능한 부분)
2. **실측용 벤치마크 코드 준비** — `PerformanceDetailCacheVsDbBenchmarkIT`(#53)와 동일한 패턴으로
   `PerformanceSearchResponseTimeBenchmarkIT`를 작성해뒀다(`@Disabled`, 로컬 docker compose로
   ES를 띄운 뒤 담당자가 직접 실행). 아래 "결과" 표는 이 테스트를 실행하면 채울 수 있는 형태로
   미리 짜놨다.

## 병목 후보 점검

| 후보 | 현재 상태 | 판단 |
| --- | --- | --- |
| 점수 계산이 불필요한 조건의 필터 분리 | `genre`/`region`은 이미 `bool.filter`(비scoring, 캐시 가능)로 분리돼 있다(`PerformanceSearchService:61-66`) | **이미 적용됨** — 추가 개선 불필요 |
| `_source` 필드 크기 | `PerformanceDocument`의 모든 필드(`title`/`artist`/`venueName`/`region`/`genre`/`posterUrl`/`createdAt`)를 `toResponse()`가 전부 사용한다 | 응답에 안 쓰는 필드가 없어 `_source` 제한의 여지가 없음 — **대상 아님** |
| 필드 매핑 | `title`/`artist`는 `Text(nori) + keyword` 멀티필드, `region`/`genre`는 `Keyword`, `createdAt`은 `Date` — 검색·필터·정렬 각 용도에 맞는 타입을 쓰고 있다 | 매핑 자체는 **이미 적절** |
| 샤드/레플리카 설정 | `performance-settings.json`: `number_of_shards=1, number_of_replicas=0` | 이 프로젝트 데이터 규모(수동 등록, 수천 건 이하 예상)에서는 샤드 1개가 오히려 맞다 — 샤드를 늘리면 작은 인덱스에서 코디네이터 오버헤드만 늘어난다. **변경 보류**(데이터가 수십만 건 규모로 커지면 재검토) |
| 결과 필드 크기/페이징 방식 | `Pageable` 기반 `from+size`. 깊은 페이지(`from`이 커질수록)는 ES가 `from+size`만큼 다시 정렬해야 해 비용이 커지고, `index.max_result_window`(기본 10,000)를 넘기면 예외가 난다 | 이슈가 제안한 `search_after`는 커서 기반이라 현재 페이지 번호 기반 API 계약(`PerformanceSearchResponse.page`)과 안 맞아 API를 바꿔야 한다 — **이번 범위에서는 보류**, 아래 "적용하지 않은 개선" 참고 |
| 전체 매치 수 집계(`track_total_hits`) | 기본값(무제한 정확 집계) — 데이터가 많아지면 "정확히 몇 건 일치하는지"를 끝까지 세는 비용이 매 요청마다 든다 | **개선 적용** — 상한을 둬서 그 비용을 없앤다. 아래 "적용한 개선" 참고 |
| 자동완성(prefix 검색) | 없음 — `edge_ngram`/`search_as_you_type` 필드 미도입 | 이슈 Notes가 "필요시"로 남겨뒀고, 실제 수요(클라이언트 요구)가 아직 없다 — **이번에는 도입하지 않음**, 아래 "자동완성 결정" 참고 |

## 적용한 개선 — `track_total_hits` 상한

`PerformanceSearchService.searchViaElasticsearch()`에 `withTrackTotalHitsUpTo(10_000)`을
추가했다. ES는 기본적으로 쿼리에 매치하는 문서 수를 정확히 세는데, 인덱스가 커질수록 이 집계
자체가 비용이 된다(matching set이 클수록 "정확한 개수"를 끝까지 세야 함). 상한을 걸면 ES가
그 수를 넘는 순간 "더 세지 않고 `{value: 10000, relation: "gte"}`로 응답"하도록 바뀐다.

- **왜 지금 적용했는가**: 현재 데이터 규모(수동 등록, 수천 건 이하)에서는 효과가 측정되지
  않을 가능성이 높다 — 매치 수가 10,000건을 넘는 검색 자체가 거의 없기 때문이다. 다만 이
  설정은 결과 정확성을 전혀 해치지 않고(사용자가 10,000건 넘는 페이지까지 내려가는 일은
  없음), 코드 변경 자체의 회귀 위험이 0에 가까워, 인덱스가 커진 뒤 다시 돌아와 추가할 이유가
  없는 "지금 해둬도 손해 없는" 개선이다.
- **검증**: 실제 ES 없이도 빌드된 쿼리에 설정이 반영됐는지 `PerformanceSearchServiceTest.
  검색_쿼리에_track_total_hits_상한이_설정된다()`로 확인했다 — `NativeQuery.getTrackTotalHitsUpTo()`가
  10,000인지 단위 테스트로 검증(ArgumentCaptor로 실제 전달된 Query 캡처).
- **Before/After 수치**: 위 측정 환경 제약으로 **테스트 예정**. 현재 데이터 규모에서는
  차이가 거의 안 날 것으로 예상하지만, 수치 없이 그렇게 단정하지 않고 벤치마크로 확인할 것.

## 적용하지 않은 개선과 그 이유

- **`search_after` 기반 커서 페이지네이션**: 깊은 페이지 비용을 줄이지만, 현재
  `PerformanceSearchResponse`가 `page`(페이지 번호) 계약으로 돼 있어 커서 기반으로 바꾸면
  API 응답 계약이 깨진다. 공연 검색은 실제로 사용자가 깊은 페이지(수십 페이지 이상)까지 내려갈
  가능성이 낮다고 보고, API를 깨는 비용이 지금 당장의 이득보다 크다고 판단해 보류했다. 깊은
  페이지 조회가 실제로 많이 일어난다는 게 확인되면(k6 부하 테스트 이후) 재검토한다.
- **자동완성(`edge_ngram`/`search_as_you_type`)**: 새 필드 추가 + 전체 재색인이 필요한
  매핑 변경이고, 현재 API/클라이언트 어디에도 자동완성 요구가 명시돼 있지 않다. 수요 없이
  매핑만 미리 늘려두는 건 색인 크기와 재색인 비용만 늘리는 선반영이라, 실제 요구가 생기면
  별도 이슈로 다루기로 결정했다.
- **샤드 수 증가**: 위 표에서 설명한 대로 현재 데이터 규모에서는 샤드 1개가 더 맞다.

## 결과 (테스트 예정)

`PerformanceSearchResponseTimeBenchmarkIT`(`@Disabled`)를 로컬에서 docker compose로 ES를
띄운 뒤 실행하면 아래 표를 채울 수 있다. 재측정 방법: 테스트 클래스의 `@Disabled` 줄을 지우고
`./gradlew test --tests "*.PerformanceSearchResponseTimeBenchmarkIT"` 단독 실행.

| 쿼리 모양 | 평균 | 최소 | 최대 | p95 |
| --- | --- | --- | --- | --- |
| 키워드만 ("콘서트", 필터 없음) | 테스트 예정 | 테스트 예정 | 테스트 예정 | 테스트 예정 |
| 키워드 + 장르·지역 필터 | 테스트 예정 | 테스트 예정 | 테스트 예정 | 테스트 예정 |
| 필터만 (키워드 없음) | 테스트 예정 | 테스트 예정 | 테스트 예정 | 테스트 예정 |
| 전체 조회 (키워드·필터 없음, 최신순) | 테스트 예정 | 테스트 예정 | 테스트 예정 | 테스트 예정 |

`track_total_hits` 상한 적용 전/후 비교도 같은 벤치마크로 측정할 수 있으나(커밋 전/후 체크아웃
후 재실행), 위에서 밝힌 대로 현재 데이터 규모(벤치마크 시드 2,000건)에서는 유의미한 차이가
없을 가능성이 높다 — 이 역시 추측이 아니라 실행해서 확인해야 한다.

## 한계

- 이번 작업 환경에 Docker가 없어 실측을 전혀 하지 못했다. 병목 분석은 코드·설정 리뷰만으로
  진행했고, 실제 느린 지점이 분석과 다를 가능성을 배제할 수 없다 — 벤치마크 실행이 최우선 후속 작업.
- 벤치마크 코드의 시드 데이터(2,000건, 장르·지역 균등 분포)는 실제 서비스 데이터 분포와 다를
  수 있다(`performance-search-explain-analysis.md`의 "발견 2"와 같은 한계).
- k6 기반 TPS/동시 요청 측정은 이번 범위에 포함하지 않았다 — 애플리케이션 컨텍스트를 직접
  띄운 순차 벤치마크로만 응답시간을 본다.

## 체크리스트 대응

- [ ] 검색 API의 개선 전 응답시간 기준선 — **테스트 예정**(벤치마크 코드는 준비됨, 측정 환경 없어 미실행)
- [x] 병목 원인 분석 결과 정리 — 위 "병목 후보 점검" 표
- [ ] 개선 적용 후 Before/After 수치 비교 — **테스트 예정**(위와 동일한 이유)
- [x] 기존 검색 테스트(`PerformanceSearchServiceTest`) 통과 확인 — 4건 모두 통과(ES/Postgres
      Testcontainers가 필요한 `PerformanceSearchIT`/`PerformanceSearchFallbackIT`는 이번 환경에서
      실행 불가 — Docker 있는 환경/CI에서 재확인 필요)
- [x] 결과가 `docs/perf/` 하위에 기록됨 — 이 문서

## 참고

- 관련: #90(검색 인덱싱 + nori), #93(검색 API 응답 확인), `docs/tradeoffs/search/why-elasticsearch.md`,
  `docs/tradeoffs/search/queryDSL-vs-es-role-split.md`
- 벤치마크 코드: `src/test/java/.../performanceseat/search/PerformanceSearchResponseTimeBenchmarkIT.java`
