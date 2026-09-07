# QueryDSL vs Elasticsearch 역할 분리

관련 이슈: #86 (QueryDSL vs ES 역할 분리 방향 확정, 노주희)
전제: #82 (QueryDSL 날짜·장르·지역 복합 쿼리 구현), #83 (실행계획 EXPLAIN 분석)

이 문서가 다루지 않는 것 (형제 이슈에서 별도로 결정):
- **ES를 왜 쓰는가** → #91 (★ ES 선택 이유 트레이드오프 문서화, 신선우)
- **DB→ES 동기화 방식·주기** → #89 (DB ↔ ES 동기화 전략 결정 리뷰, 노주희) — 이 문서는 "무언가
  동기화된다"만 전제하고 구체적 전략은 다루지 않는다
- **ES 색인 구현 자체(nori 분석기, 매핑 등)** → #90 (공연 검색 인덱싱 + nori 적용, 신선우) —
  구체적 패키지 구조·매핑 설계는 구현자 판단 영역
- **폴백 응답 검증** → #93 (검색 API 응답 확인, 테스트)
- **인기순 랭킹** → #94 (인기 공연 랭킹, Redis Sorted Set) — 이 문서 표에는 "혼동 방지용"으로만
  한 줄 언급

이 문서가 다루는 것은 오직 **"어떤 조회 요구사항을 어느 저장소가 담당하는가 + 그 경계의 기본
원칙(폴백 조건/일관성 규칙)"**이다.

> 초안 상태 — 신선우(공연 검색 구현자) 리뷰 전. 실측치가 필요한 항목은 "TBD"로 남겨둠.

# 현재 상태 (코드 기준)

- `PerformanceRepositoryImpl.search()`(QueryDSL)는 genre/region/날짜 범위 복합 조회를 이미
  구현했지만, `PerformanceService`/`PerformanceController`가 아직 호출하지 않는다 — 즉
  `GET /api/performances`는 필터 없는 `findAll()`만 제공 중이고 검색 API는 미배선 상태다.
- `PerformanceSearchCondition`에 자유 텍스트(제목/아티스트 키워드) 필드가 없다 — 구조화된 필터만
  존재.
- `build.gradle`/`docker-compose.yml`에 Elasticsearch 의존성·컨테이너가 아직 없다 — ES 자체가
  코드베이스에 존재하지 않는 상태에서 역할부터 먼저 정하는 것이 이 이슈의 목적이다.

# 조회 요구사항별 책임표

| 요구사항 | 담당 | 근거 |
| --- | --- | --- |
| 장르 필터 (genre 단일값) | **QueryDSL** (PostgreSQL) | 이미 구현됨(`PerformanceRepositoryImpl.genreEq`). `performances(genre)` 단일 인덱스 존재(#73) |
| 지역 필터 (region 단일값) | **QueryDSL** | 이미 구현됨. `performances(region, genre)` 복합 인덱스가 region 단독 조회도 커버(#73) |
| 날짜 범위 필터 (공연 회차 기준) | **QueryDSL** | 이미 구현됨(EXISTS 서브쿼리, `PerformanceSchedule.performanceDatetime`). `performance_schedules(performance_datetime)` 인덱스 존재 |
| 장르+지역+날짜 복합 조합 | **QueryDSL** | #73에서 복합 인덱스로 실측 검증됨(Filter 단계 제거, 지연시간 40~56%↓) — 정확한 등가/범위 조건이라 ES로 옮길 이유 없음 |
| 공연명/아티스트 자유 텍스트 검색 (형태소 분석 필요, 오타·부분어 허용) | **Elasticsearch** (nori) | PostgreSQL `LIKE '%keyword%'`는 인덱스를 못 타 Seq Scan이고, 한국어 띄어쓰기·조사 변형을 다루지 못함 — nori 분석기가 이 요구사항의 존재 이유(#91) |
| 자동완성 (타이핑 중 추천) | **Elasticsearch** | edge-ngram/completion suggester 필요 — QueryDSL/SQL로는 매 keystroke마다 LIKE 스캔이라 비현실적 |
| 검색 관련도 기반 정렬 (검색어와의 일치도 score) | **Elasticsearch** | relevance score는 ES의 기본 기능이고 QueryDSL/SQL에는 대응 개념이 없음 |
| 인기 공연 랭킹 (예매량 기준 정렬) | **Redis Sorted Set** (QueryDSL도 ES도 아님) | ROADMAP 6주차에 별도로 명시된 신선우 작업 — 검색 관련도가 아니라 카운터 기반 랭킹이라 ES 책임이 아님. 혼동하지 않도록 이 표에 명시 |
| 필터 없는 목록/상세 기본 조회 | **QueryDSL/JPA** (PostgreSQL) | 검색이 아닌 단순 CRUD 조회 — `findAll`/`findById` 그대로 |

# API 기본 경로 & 폴백 정책 (제안)

- **구조화된 필터만 있는 요청** (`genre`/`region`/날짜 범위, 키워드 없음): 처음부터 QueryDSL 전용
  경로. ES 장애와 무관 —애초에 ES를 거치지 않으므로 폴백 대상이 아니다.
- **키워드(`q`)가 포함된 요청**: 기본 경로는 Elasticsearch. ES 장애 시 PostgreSQL
  `title ILIKE '%keyword%' OR artist ILIKE '%keyword%'`로 폴백 — 단, 형태소 분석·관련도 정렬·
  자동완성은 폴백 상태에서 제공하지 않는다(단순 부분일치 + 최신순 정렬로 축소). 이 축소를 API
  응답에 어떻게 표시할지(예: `degraded: true` 필드)는 TBD — 신선우 구현 시 결정.
- 구조화된 필터(genre/region/날짜)와 키워드(`q`)가 **함께** 오는 요청을 어느 경로로 보낼지는 TBD.
  현재 후보: ES가 필터까지 함께 처리(색인에 genre/region/date 필드 포함) vs ES로 키워드 매칭한
  ID 목록을 받아 QueryDSL로 재필터링. 후자는 두 저장소를 순차 호출해 지연시간이 늘고, 전자는
  DB↔ES 동기화 범위가 넓어진다 — #91 진행 시 실측 후 확정.

# 페이지네이션·정렬·응답 일관성 규칙 (제안)

- 페이지네이션 파라미터(`page`/`size`)와 응답 스펙(`PerformanceResponse` 목록 + 총 개수)은 경로가
  QueryDSL이든 ES든 동일한 형태를 유지한다 — 클라이언트가 어느 저장소를 탔는지 알 필요가 없어야
  한다.
- 정렬 기준은 경로별로 의미가 다르므로 이름을 분리한다: QueryDSL 경로는 `createdAt desc`(현재
  구현)나 날짜순, ES 경로는 관련도(`_score`) 기본 정렬 — 정렬 옵션 자체를 사용자가 섞어서 요청하는
  케이스(예: 키워드 검색 결과를 관련도 대신 날짜순으로)는 지원 범위를 TBD로 남김.
- 폴백 발생 시 결과 개수·순서가 평소(ES 정상)와 달라질 수 있다는 점은 감수한다(운영 단순성 우선) —
  대신 두 경로 모두 동일 페이지네이션 계약을 지켜 클라이언트 코드가 깨지지는 않게 한다.

# 패키지/서비스 경계 (원칙만 — 구체 구조는 #90 구현자 판단)

- 중복 구현 방지를 위한 최소 원칙만 명시한다: `PerformanceRepositoryCustom`/
  `PerformanceRepositoryImpl` (QueryDSL)은 구조화된 필터 전용으로 유지하고, 키워드 검색 로직을
  여기 섞지 않는다. ES 연동 코드가 이 QueryDSL 리포지토리를 감싸거나 대체하지 않도록 경계를 둔다.
- 두 경로를 어떤 서비스/패키지 구조로 조합할지(오케스트레이션 위치, 서브패키지명 등)는 실제 구현
  이슈(#90)에서 신선우가 정한다 — 이 문서는 강제하지 않는다.

# 남은 TBD (신선우 리뷰 필요)

- [ ] 필터+키워드 동시 요청 처리 방식 (ES 필드 확장 vs 2단계 조회) — #90 구현 시 확정
- [ ] ES 폴백 상태를 응답에 표시할지 여부와 필드명 — #93 검증 범위와 함께 확정
- [ ] 이 문서가 전제하는 "동기화됨" 가정이 #89 결정과 실제로 맞물리는지 교차 확인 (예: #89가 배치
      동기화를 택하면 폴백 조건에 "동기화 지연 중" 케이스가 추가로 필요할 수 있음)
