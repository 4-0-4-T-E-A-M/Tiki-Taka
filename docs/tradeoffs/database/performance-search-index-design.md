# 공연 검색(장르·지역·날짜) 인덱스 설계

관련 이슈: #73 (인덱스 설계 트레이드오프 문서화), 근거: #72 (실행계획 EXPLAIN 분석),
전제: #71 (QueryDSL 날짜·장르·지역 복합 쿼리 구현)

# 문제 상황

#71에서 만든 공연 검색(`PerformanceRepository.search()`)은 장르·지역·날짜를 선택적으로 조합해
조회한다. `docs/db/ddl.sql`은 `performances(genre)`, `performances(region)`,
`performance_schedules(performance_id)`, `performance_schedules(performance_datetime)`,
`performance_schedules(status)`에 단일 컬럼 인덱스를 정의해 두었지만, 두 엔티티 모두
`@Table(indexes = ...)` 선언이 없어 `ddl-auto=update`가 관리하는 실제 런타임 스키마에는
**이 인덱스들이 전혀 적용되어 있지 않다**(#72에서 `\d performances` 등으로 직접 확인, 기본 키
인덱스만 존재). 즉 `ddl.sql`은 설계 의도일 뿐 실제로 반영되지 않은 상태다.

이 상태로 두면 두 가지 결정을 미루게 된다.

- 단일 컬럼 인덱스를 그대로 반영할지, 장르+지역 복합 조건을 위한 복합 인덱스를 추가할지
- 복합 인덱스를 추가한다면 컬럼 순서(선두 컬럼)를 어떻게 정할지

인덱스는 읽기 성능을 올리지만 모든 쓰기(INSERT/UPDATE)에 갱신 비용이 붙고 저장 공간을 차지하므로,
근거 없이 여러 개를 늘어놓는 결정은 피해야 한다.

# 현재 인덱스 구성과 대표 검색 패턴

`performances` 20,000행 / `performance_schedules` 39,784행 (공연당 회차 1~3개, genre·region은
각 enum 값에 균등 분포)을 시딩해 #72에서 확인한 대표 검색 패턴은 다음과 같다.

| 쿼리 | 조건 | 실제 매칭 행 수(20,000건 중) |
| --- | --- | --- |
| Q1 | genre만 (CONCERT) | 3,312행 (genre 6종 중 1종, 선택도 낮음) |
| Q2 | region만 (SEOUL) | 1,549행 (region 13종 중 1종, genre보다 선택도 높음) |
| Q4 | genre + region | 258행 |
| Q3/Q5 | + 날짜 기간(30일) | performance_schedules 39,784건 중 9,818건과 조인 |

genre는 6개 값, region은 13개 값이라 **region이 genre보다 선택도가 높다**(값 하나당 매칭되는
평균 행 수가 더 적다) — 이는 추측이 아니라 실제 시딩 데이터의 행 수로 확인한 값이다.

# 고려한 대안 비교

## A. 단일 컬럼 인덱스만 (ddl.sql 그대로) vs B. 복합 인덱스 추가

`performances(genre)`, `performances(region)`을 각각 추가한 상태에서 genre+region 조합 쿼리(Q4)를
EXPLAIN한 결과, PostgreSQL 플래너는 **두 인덱스를 함께 쓰지 않고 하나만(이 경우 region) 선택해
Bitmap Index Scan을 하고 나머지(genre)는 결과에 대한 Filter로 처리**했다. 즉 단일 컬럼 인덱스
2개만으로는 조합 조건에서 "인덱스 두 개를 다 활용"하는 게 아니라 "하나만 타고 나머지는 걸러낸다".

`(region, genre)` 복합 인덱스를 추가로 만들어 같은 쿼리를 다시 측정하니:

| 쿼리 | 단일 인덱스 2개(genre 인덱스 미사용, region 인덱스 + Filter) | 복합 인덱스 `(region, genre)` |
| --- | --- | --- |
| Q4 (genre+region) | 0.914ms, buffers 332 | **0.401ms, buffers 180** |
| Q5 (genre+region+날짜) | 5.129ms, buffers 691 | **3.089ms, buffers 545** |

복합 인덱스를 쓰면 `Index Cond`에 genre·region 조건이 모두 들어가 Filter 단계 자체가 사라지고,
지연시간이 Q4는 약 56%, Q5는 약 40% 줄었다(buffer 접근 수도 각각 약 46%, 21% 감소). 이 데이터
규모·분포에서도 측정 가능한 차이였다.

## 복합 인덱스 컬럼 순서: `(region, genre)` vs `(genre, region)`

선두 컬럼을 정할 때 두 가지를 함께 고려했다.

1. **선택도**: region이 genre보다 선택도가 높다(위 표). 동등 조건 복합 인덱스에서는 일반적으로
   선택도가 높은 컬럼을 앞에 두는 것이 카디널리티를 더 빨리 좁힌다.
2. **단일 조건 쿼리 재사용**: btree 복합 인덱스는 선두 컬럼에 대해서는 접두사(prefix) 스캔이
   가능하지만, 두 번째 컬럼만으로는 효율적으로 탐색할 수 없다. `(region, genre)`로 만들면
   `region`만 조건으로 거는 Q2도 이 복합 인덱스 하나로 커버되지만, `(genre, region)`으로 만들면
   Q2는 이 인덱스를 못 쓴다.

실제로 `(region, genre)` 인덱스를 만든 뒤 `performances(region)` 단일 인덱스를 **삭제**하고 Q2를
다시 측정해보니, 플래너가 복합 인덱스를 접두사로 그대로 사용해 단일 인덱스가 있을 때와 동등한
성능(0.72ms, Bitmap Index Scan on `performances_region_genre_idx`, `Index Cond: region='SEOUL'`)을
유지했다. 즉 `(region, genre)` 복합 인덱스 하나가 "region 단독 조회"와 "region+genre 조합 조회"를
모두 커버해, `performances(region)` 단일 인덱스는 중복이 되어 제거할 수 있었다.

반대로 `genre` 단독 조회(Q1)는 이 복합 인덱스의 두 번째 컬럼이라 커버되지 않으므로
`performances(genre)` 단일 인덱스는 그대로 유지해야 한다.

# 최종 선택

`performances` 테이블에는 인덱스 3개가 아니라 **2개**만 둔다.

- `performances(genre)` — 단일 인덱스 유지 (genre 단독 조회를 커버하는 유일한 경로)
- `performances(region, genre)` — 복합 인덱스 신설, `performances(region)` 단일 인덱스는
  **만들지 않는다**(복합 인덱스가 접두사로 대체하므로 중복)

`performance_schedules`는 `ddl.sql`이 정의한 3개 인덱스를 그대로 둔다.

- `performance_schedules(performance_datetime)` — 날짜 범위 조건(EXISTS 서브쿼리, Q3/Q5)이
  Seq Scan 대신 이 인덱스의 Bitmap Index Scan을 타는 것을 #72에서 확인했다.
- `performance_schedules(performance_id)` — 이번 검색 쿼리들(EXISTS가 Hash Semi Join으로
  평탄화됨)에서는 쓰이지 않지만, `PerformanceService.getSchedules`/`deletePerformance`가 호출하는
  `findAllByPerformanceId`가 이 컬럼으로 직접 조회하므로 여전히 필요하다.
- `performance_schedules(status)` — 이번 검색 쿼리도, 현재 코드베이스의 다른 조회도 아직
  `status`로 필터링하지 않아 지금 당장 쓰이는 곳은 없다. 다만 같은 5주차 이슈인 #75(공연 오픈
  스케줄러)가 "SCHEDULED 상태이면서 오픈 시각이 지난 회차"를 조회할 예정이라 미리 남겨둔다.

# 감수하는 단점과 한계

- **`(region, genre)` 복합 인덱스는 genre 단독 조회에 도움이 안 된다** — genre 단독 조회는
  여전히 `performances(genre)` 인덱스에 의존한다. 두 인덱스를 유지하는 이상 genre, region 각각
  단독으로도, 조합으로도 인덱스를 타지만 인덱스 개수(2개)와 쓰기 비용은 여전히 발생한다.
- **측정은 이번 이슈의 합성 데이터(2만 행, 균등 분포, 단일 실행) 기준이다** — #72에서 밝힌 한계와
  동일하게, 실제 서비스처럼 특정 장르·지역에 조회가 쏠리는 비균일 분포나 훨씬 큰 데이터 규모에서는
  플래너의 선택이 달라질 수 있다. 특히 데이터가 더 커지면 복합 인덱스의 이득이 이번 측정(40~56%)보다
  커질 가능성이 높다(Seq Scan 비용은 테이블 크기에 비례해 커지는 반면 인덱스 스캔 비용은 상대적으로
  덜 늘어난다) — 실측하지 않았으므로 가능성으로만 남긴다.
- **이 문서는 분석·결정까지만 다룬다** — 실제로 엔티티에 `@Table(indexes = ...)`를 추가하거나
  마이그레이션을 작성해 이 결정을 런타임 스키마에 반영하는 작업은 이 이슈(#73)의 체크리스트에
  포함되어 있지 않다. 별도 구현 이슈로 이어가야 한다(현재 그런 이슈가 없다는 것도 #72/#73 작업
  중 확인한 사실이다).

# 실측 테스트 결과

`docs/perf/raw/explain-after.txt`(단일 인덱스만 적용), `docs/perf/raw/explain-composite.txt`
(`(region, genre)` 복합 인덱스 추가), `docs/perf/raw/explain-composite-drop-region.txt`
(`performances(region)` 단일 인덱스 삭제 후 Q2 재확인)에 `EXPLAIN (ANALYZE, BUFFERS)` 원본을
보관했다. 반복 측정에 의한 평균·분산은 구하지 않았고(각 1회 실행), 절대 수치보다는 실행계획이
바뀌는지(Filter 단계 소거 여부)를 근거로 삼았다 — 자세한 측정 환경·한계는 #72 문서
(`docs/perf/performance-search-explain-analysis.md`)를 따른다.

# 정리

`docs/db/ddl.sql`이 문서화한 단일 컬럼 인덱스를 그대로 반영하는 대신, genre+region 조합 조회가
실제 접근 패턴에 있다는 점과 실측 결과(복합 인덱스가 조합 조회를 30~55% 빠르게 만듦)를 근거로
`performances(region)` 단일 인덱스를 `performances(region, genre)` 복합 인덱스로 대체하기로
했다. `performances(genre)`는 genre 단독 조회의 유일한 경로라 그대로 유지한다.
`performance_schedules`의 세 인덱스는 검색 쿼리·기존 조회(`findAllByPerformanceId`)·예정된 후속
이슈(#75)를 근거로 그대로 유지한다. 실제 인덱스 반영(엔티티 애노테이션 또는 마이그레이션)은 이
문서의 범위 밖이며 별도 구현 이슈가 필요하다.
