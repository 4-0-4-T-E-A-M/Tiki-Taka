# 예매 검색(QueryDSL) 실행계획 EXPLAIN 분석

Issue #70. #69에서 구현한 `ReservationRepositoryImpl.search()`(userId·simulationId·scheduleId·
status·기간 복합 조건)에 대해 대표 조건 조합을 골라 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)`로
분석한 기록이다. `docs/perf/performance-search-explain-analysis.md`(Issue #72)와 동일한 형식·
용어를 사용한다.

## 테스트 환경

- PostgreSQL 16 (`docker compose up -d postgres`, 로컬 1개 인스턴스)
- 애플리케이션 스키마: `spring.jpa.hibernate.ddl-auto=update`로 생성된 실제 런타임 스키마
  (`docs/db/ddl.sql`과는 별개, 아래 "발견 1" 참고)
- 데이터 규모: `reservations` 50,000행 (`user_id` 1~5,000, `simulation_id` 1~2,000,
  `schedule_id` 1~500, `status`는 5개 enum 값에 균등 분포, `created_at`은 현재 시각 기준
  0~60일 전 범위에 균등 분포, 고정 시드(`setseed(0.42)`)로 생성)
- 측정 방법: 각 쿼리를 인덱스 적용 전/후로 한 번씩 `EXPLAIN (ANALYZE, BUFFERS)` 실행
  (반복 측정에 의한 평균/분산은 구하지 않음 — 한계 항목 참고)

## 발견 1 — 현재 런타임 스키마에는 `docs/db/ddl.sql`의 인덱스가 없었다

`docs/db/ddl.sql`은 `reservations(user_id)`, `reservations(simulation_id)`,
`reservations(schedule_id)`, `reservations(status)`에 인덱스를 정의하고 있었지만, `Reservation`
엔티티에 `@Table(indexes = ...)` 선언이 없어 `ddl-auto=update`가 관리하는 실제 스키마에는
**기본 키 인덱스만 존재하고 위 4개 인덱스는 전혀 적용되어 있지 않았다** (`\d reservations`로
직접 확인). `docs/perf/performance-search-explain-analysis.md`(#72)에서 `performances`,
`performance_schedules`에 대해 확인된 것과 동일한 갭이다.

즉 `ddl.sql`은 설계 문서일 뿐 런타임에 자동으로 반영되지 않는다. 이 분석은 그래서 두 단계로
나눠 진행했다.

- **BEFORE**: 현재 런타임 그대로(인덱스 없음, PK만 존재)
- **AFTER**: `ddl.sql`에 문서화된 4개 인덱스 + 정렬에 쓰이는 `created_at` 인덱스까지 총 5개를
  수동으로 추가한 뒤 재측정

## 대표 검색 조건 5개

`ReservationRepositoryImpl.search()`가 지원하는 필터(`userId`, `simulationId`, `scheduleId`,
`status`, `createdFrom`/`createdTo`)를 조합해, 실제 조회 화면에서 쓰일 법한 대표 조합을
선정했다. 모든 쿼리는 `ORDER BY created_at DESC` + `LIMIT 20 OFFSET 0`을 포함한다
(`ReservationRepositoryImpl`의 정렬·페이징 방식과 동일).

| 번호 | 조건 | 시나리오(가정) |
| --- | --- | --- |
| Q1 | `user_id = 123` 단독 | 내 예매 내역 전체 조회 |
| Q2 | `status = 'CONFIRMED'` 단독 (선택도 낮음, 전체의 약 20%) | 확정된 예매 전체 조회 (운영/통계용) |
| Q3 | `user_id = 123 AND status = 'CONFIRMED'` | 내 예매 중 확정된 것만 조회 |
| Q4 | `schedule_id = 250 AND status = 'CONFIRMED' AND` 최근 7일 | 특정 회차의 최근 확정 예매 현황 (운영/모니터링) |
| Q5 | `created_at`이 최근 7일 범위 | 최근 예매 전체 조회 |

## 실행계획 비교 (BEFORE → AFTER)

| 쿼리 | BEFORE (인덱스 없음) | AFTER (단일 인덱스 5개 적용) | Execution Time |
| --- | --- | --- | --- |
| Q1 user_id 단독 | `Seq Scan` (50,000행 스캔, 49,986행 필터 제거) | `Bitmap Index Scan` on `idx_reservations_user_id` | 4.22ms → 0.08ms |
| Q2 status 단독 | `Seq Scan` (50,000행 스캔, 37,717행 제거) | `idx_reservations_status`가 아니라 **`idx_reservations_created_at`을 역순 스캔**하며 `LIMIT 20` 도달 시 조기 종료 (아래 "발견 2") | 6.19ms → 0.15ms |
| Q3 user_id+status | `Seq Scan` (49,994행 제거) | `idx_reservations_user_id`만 사용, `status`는 `Filter`로 처리 | 3.49ms → 0.04ms |
| Q4 schedule_id+status+기간 | `Seq Scan` (49,997행 제거) | `idx_reservations_schedule_id` + `idx_reservations_created_at`을 `BitmapAnd`로 결합, `status`는 `Filter` | 2.82ms → 0.79ms |
| Q5 기간 단독 | `Seq Scan` (44,202행 제거) | `idx_reservations_created_at` 직접 스캔(Index Cond, Filter 없음) | 9.03ms → 0.05ms |

전체 EXPLAIN 원본 출력은 `docs/perf/raw/reservation-explain-before.txt`,
`docs/perf/raw/reservation-explain-after.txt`에 보관했다.

## 발견 2 — 정렬용 인덱스가 필터용 인덱스보다 유리할 수 있다 (`ORDER BY` + `LIMIT` 조합)

Q2는 `status` 선택도가 20%로 낮지 않은데도(37,717/50,000행이 조건에 안 맞음),
플래너는 `idx_reservations_status`를 타지 않고 `idx_reservations_created_at`을 역순으로
스캔하면서 `status` 조건은 `Filter`로만 검사했다. `ORDER BY created_at DESC LIMIT 20`이 걸려
있어서, 정렬 순서 그대로인 `created_at` 인덱스를 위에서부터 훑다가 조건에 맞는 20건을 채우는
순간 바로 멈추는 게, "status로 12,243행을 다 걸러낸 뒤 정렬"하는 것보다 싸다고 판단한 것이다.

이는 `#72`의 "발견 2"(단일 컬럼 인덱스 2개가 `BitmapAnd`로 조합되지 않고 하나만 선택됨)와
같은 종류의 현상으로, **"필터 조건에 맞는 인덱스가 있다고 해서 그 인덱스를 항상 타는 것은
아니다"**를 보여준다. `LIMIT`이 있는 목록 조회 API에서는 정렬 컬럼(`created_at`) 인덱스 하나가
여러 필터 조건의 인덱스보다 더 넓게 재사용될 수 있다는 뜻이기도 하다.

## 발견 3 — 복합 인덱스는 효과가 있지만 쓰기 비용 트레이드오프가 있다

Q4에 `(schedule_id, status, created_at)` 복합 인덱스를 추가로 적용해보면 실행 시간이
0.79ms → 0.11ms, 버퍼 접근이 28블록(`hit=10 read=18`) → 12블록(`hit=9 read=3`)으로
더 줄어든다(원본: `docs/perf/raw/reservation-explain-after.txt` 마지막 블록).

다만 이 복합 인덱스는 **`status` 컬럼을 포함**한다. `Reservation`은 상태가
`PENDING_PAYMENT → CONFIRMED/FAILED/EXPIRED/CANCELED`로 자주 바뀌는 엔티티이고
([`booking/CLAUDE.md`](../../src/main/java/io/github/team404/tikitaka/booking/CLAUDE.md)에
정리된 좌석 상태 전이와 맞물려, 콘서트 오픈 순간 대량 동시 쓰기가 몰리는 구간에서 `status`가
집중적으로 갱신된다), 이 컬럼이 인덱스에 들어가면 상태가 바뀔 때마다 인덱스 항목도 갱신해야 해서
쓰기 비용이 늘어난다. 이번 분석은 읽기 성능만 측정했고 쓰기 비용은 실측하지 않았으므로,
**이 복합 인덱스는 채택하지 않고 "필요성이 실제로 확인되면 재검토할 후보"로만 기록한다**
(코드에는 반영하지 않음, 실험 후 `DROP INDEX` 완료).

## 코드 반영

위 단일 인덱스 5개는 부작용 없이 모든 대표 조건에서 개선이 확인되어
[`Reservation.java`](../../src/main/java/io/github/team404/tikitaka/booking/entity/Reservation.java)에
`@Table(indexes = {...})`로 반영했다. `ddl-auto=update`가 다음 기동 시 이 인덱스들을 생성한다.

## 추정 행 수 vs 실제 행 수

`EXPLAIN ANALYZE` 직전에 `ANALYZE reservations`를 수행했고 테스트 데이터가 각 컬럼에 균등
분포하도록 생성됐기 때문에, 추정 행 수와 실제 행 수가 대체로 근접했다(Q2 추정 12,223 vs
실제 12,283). 실제 서비스처럼 특정 인기 공연 회차에 예매가 쏠리는 비균일 분포에서는
추정-실제 오차가 이보다 클 수 있다.

## 한계

- 반복 측정 없이 각 쿼리를 1회씩만 실행했다. 절대 수치(ms)는 참고용이며, 이 문서에서 의미
  있게 보는 것은 실행계획의 **Scan 방식 변화**(Seq → Bitmap/Index Scan)와 **인덱스 선택 여부**다.
- 데이터가 균등 분포로 합성 생성되어, 실제 서비스에서 특정 유저·회차에 조회가 쏠리는 분포와는
  다르다.
- 50,000행은 초기 서비스 규모를 가정한 값이며, 이보다 훨씬 큰 규모나 동시 조회 부하 상황에서의
  실행계획은 별도 확인이 필요하다(테스트 예정, 이번 이슈 범위 아님).
- `(schedule_id, status, created_at)` 복합 인덱스의 **쓰기 비용**은 측정하지 않았다 — 읽기
  개선 수치만 참고용으로 남기고 채택 여부는 실제 쓰기 부하 데이터가 쌓인 뒤 재검토한다.

## 인덱스 유지·변경 판단 근거 요약 (체크리스트 대응)

1. `docs/db/ddl.sql`에 문서화된 인덱스가 실제 런타임 스키마에는 반영되어 있지 않았다 —
   `Reservation.java`에 `@Table(indexes=...)`를 추가해 이 갭을 해소했다.
2. 단일 컬럼 인덱스(`user_id`, `schedule_id`, `status`, `created_at`)는 대표 조건 5개 모두에서
   Seq Scan을 없애거나 Execution Time을 크게 줄이는 효과가 확인되어 그대로 채택한다.
   (`simulation_id`는 이번 대표 쿼리에서 직접 테스트하지 않았지만, `user_id`와 동일한 패턴의
   단일 FK 컬럼이라 같은 효과를 기대할 수 있어 함께 추가했다.)
3. `ORDER BY created_at DESC LIMIT n`이 걸린 목록 조회에서는 필터 조건의 선택도가 낮아도
   정렬 컬럼(`created_at`) 인덱스가 더 유리하게 선택될 수 있다 — 향후 새 필터 조건을 추가할 때
   무조건 그 컬럼에 인덱스를 걸기보다 이 패턴을 먼저 확인한다.
4. `(schedule_id, status, created_at)` 같은 `status` 포함 복합 인덱스는 읽기 성능 개선은
   확인됐지만 쓰기 비용 근거가 없어 이번에는 추가하지 않는다 — 근거 없이 모든 조합에 인덱스를
   붙이지 않는다는 이슈 원칙에 따른 판단이다.
