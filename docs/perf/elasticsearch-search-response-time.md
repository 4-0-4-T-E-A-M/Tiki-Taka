# 공연 검색(Elasticsearch) 응답시간 — 병목 분석과 개선

Issue #116. #90/#93에서 구현한 `PerformanceSearchService`(ES 기본 경로 + PostgreSQL 폴백)의
응답 성능 기준선을 잡고, 병목 후보를 점검한 뒤 실제로 적용한 개선을 정리한다.

## 이 문서가 다루지 않는 것

이름이 비슷한 `docs/perf/performance-search-explain-analysis.md`(#72)는 **QueryDSL/PostgreSQL**
경로(`PerformanceRepository.search()`)의 `EXPLAIN ANALYZE` 분석이다. 이 문서는 **Elasticsearch**
경로(`PerformanceSearchService.searchViaElasticsearch()`)만 다룬다 — 두 경로는
`docs/tradeoffs/search/queryDSL-vs-es-role-split.md`(#93)가 정리한 대로 서로 다른 조회 패턴을
맡고 있어 병목 지점도 다르다.

## 측정 환경

- `docker compose up -d elasticsearch`로 로컬에 띄운 nori 포함 이미지(`tikitaka-elasticsearch:8.18.8-nori`,
  단일 노드) — 애플리케이션과 같은 머신, 네트워크 왕복 지연은 반영되지 않음(한계 항목 참고)
- 측정 대상: `PerformanceSearchService.search()` — 컨트롤러가 아니라 서비스 메서드를 직접 호출
- 시드 데이터: `performances` 인덱스에 2,000건(장르·지역 균등 분포, 제목 절반에 "콘서트" 포함 —
  키워드 검색이 전체가 아니라 일부만 매칭하도록)
- 측정 방법: `src/test/java/.../performanceseat/search/PerformanceSearchResponseTimeBenchmarkIT.java`
  (평소엔 `@Disabled` — 로컬 docker compose가 떠 있을 때만 수동 실행). 워밍업 5회 후 대표 쿼리
  모양 4가지를 각각 순차 30회 측정(avg/min/max/p95).

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
- **Before/After 수치**: 아래 "결과" 절 참고 — 실측 결과 예상대로 현재 시드 규모(2,000건)에서는
  유의미한 차이가 없었다.

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

## 결과

2,000건 시드 기준, `track_total_hits` 상한 적용 **후**(순차 30회):

| 쿼리 모양 | 평균 | 최소 | 최대 | p95 |
| --- | --- | --- | --- | --- |
| 키워드만 ("콘서트", 필터 없음) | 18.952ms | 12ms | 33ms | 26ms |
| 키워드 + 장르·지역 필터 | 15.775ms | 9ms | 29ms | 22ms |
| 필터만 (키워드 없음, 장르·지역) | 12.368ms | 7ms | 18ms | 15ms |
| 전체 조회 (키워드·필터 없음, 최신순) | 12.659ms | 7ms | 33ms | 18ms |

같은 조건, 상한 적용 **전**(`withTrackTotalHitsUpTo` 호출을 임시로 제거하고 재측정):

| 쿼리 모양 | 평균 | 최소 | 최대 | p95 |
| --- | --- | --- | --- | --- |
| 키워드만 ("콘서트", 필터 없음) | 15.790ms | 9ms | 21ms | 21ms |
| 키워드 + 장르·지역 필터 | 14.300ms | 7ms | 27ms | 21ms |
| 필터만 (키워드 없음, 장르·지역) | 11.852ms | 8ms | 17ms | 17ms |
| 전체 조회 (키워드·필터 없음, 최신순) | 15.755ms | 6ms | 74ms | 25ms |

**Before/After 차이는 노이즈 범위 안이다** — 일부 쿼리 모양은 오히려 적용 후가 약간 더 느리게
나왔다(예: 키워드만 15.79ms → 18.95ms). 2,000건은 `track_total_hits` 기본 동작(정확히 세기)의
비용 자체가 거의 0에 가까운 규모라, 이번 측정에서는 효과가 보이지 않는다 — "병목 후보 점검"
표에서 미리 예상한 대로다. 이 설정을 유지하는 이유는 지금 효과가 있어서가 아니라, 인덱스가
수만 건 이상으로 커졌을 때를 대비한 **회귀 위험 0의 선제 조치**이기 때문이다(적용 근거는 위
"적용한 개선" 절 참고). 데이터가 실제로 그 규모에 도달하면 재측정해 효과를 확인해야 한다.

전체 콘솔 출력(적용 후):
```
[키워드만 ("콘서트", 필터 없음)] avg=18.952ms min=12ms max=33ms p95=26ms n=30
[키워드 + 장르·지역 필터] avg=15.775ms min=9ms max=29ms p95=22ms n=30
[필터만 (키워드 없음, 장르·지역)] avg=12.368ms min=7ms max=18ms p95=15ms n=30
[전체 조회 (키워드·필터 없음, 최신순)] avg=12.659ms min=7ms max=33ms p95=18ms n=30
```

## 발견 — 키워드 검색이 필터/전체조회보다 일관되게 느리다

쿼리 모양 간 비교에서는(상한 적용 후 기준) 키워드가 섞인 쿼리(18.95ms, 15.78ms)가 필터만
쓰거나 조건이 아예 없는 쿼리(12.37ms, 12.66ms)보다 뚜렷이 느리다 — `multi_match`가 `title^2`,
`artist^2`, `venueName` 세 필드에 대해 각각 관련도 점수를 계산해야 하는 반면, filter-only·
match-all 쿼리는 비scoring 경로(filter context)를 타거나 계산이 단순하기 때문으로 보인다. 이
차이는 설정으로 없앨 수 있는 게 아니라 "텍스트 관련도 검색은 구조적으로 단순 필터보다 비싸다"는
당연한 결과이고, 2,000건 규모에서도 6ms 안팎 차이가 이미 보인다는 점은 데이터가 커질수록 이
격차가 더 벌어질 수 있다는 신호다 — 추후 재측정 시 이 쿼리 모양 간 격차의 증가율을 지켜볼 것.

## 한계

- 로컬 1대 머신, 단일 노드 ES에서 한 세트만 측정했다. 애플리케이션·ES가 같은 머신에 있어
  실제 프로덕션의 네트워크 왕복 지연은 반영되지 않는다.
- 시드 데이터 2,000건은 장르·지역이 균등 분포하도록 합성 생성했다 — 실제 서비스에서 인기
  장르·지역에 쏠리는 비균일 분포와는 다르다(`performance-search-explain-analysis.md`의
  "발견 2"와 같은 한계).
- `track_total_hits` 상한의 효과는 매치 수가 상한(10,000)을 실제로 넘는 규모에서만 나타난다 —
  이번 2,000건 시드로는 그 조건 자체를 만들지 않았으므로, "효과 없음"이 아니라 "이 조건에서는
  아직 효과가 나타날 상황이 아니다"로 읽어야 한다.
- k6 기반 TPS/동시 요청 측정은 이번 범위에 포함하지 않았다 — 애플리케이션 컨텍스트를 직접
  띄운 순차 벤치마크로만 응답시간을 봤다(동시 다발적 봇 트래픽 재현은 8주차 범위).

## 체크리스트 대응

- [x] 검색 API의 개선 전 응답시간 기준선이 기록되어 있다 — 위 "결과" 절 (상한 적용 전 표)
- [x] 병목 원인 분석 결과가 정리되어 있다 — 위 "병목 후보 점검" 표, "발견" 절
- [x] 개선 적용 후 Before/After 수치가 비교되어 있다 — 위 "결과" 절 (두 표 + 비교 서술).
      차이가 노이즈 범위라는 것 자체가 이번 측정의 결론이다 — 효과를 부풀리지 않았다.
- [x] 기존 검색 테스트(`PerformanceSearchIT`, `PerformanceSearchFallbackIT` 등)가 통과한다 —
      로컬 docker compose(ES) 기동 상태에서 `PerformanceSearchServiceTest`(4건),
      `PerformanceSearchIT`(7건), `PerformanceSearchFallbackIT`(3건) 전부 통과 확인
- [x] 결과가 `docs/perf/` 하위에 기록되어 있다 — 이 문서

## 참고

- 관련: #90(검색 인덱싱 + nori), #93(검색 API 응답 확인), `docs/tradeoffs/search/why-elasticsearch.md`,
  `docs/tradeoffs/search/queryDSL-vs-es-role-split.md`
- 벤치마크 코드: `src/test/java/.../performanceseat/search/PerformanceSearchResponseTimeBenchmarkIT.java`
