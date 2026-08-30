# 공연 검색(QueryDSL) 실행계획 EXPLAIN 분석

Issue #72. #71에서 구현한 `PerformanceRepository.search()`(장르·지역·날짜 복합 조건)가 생성하는
실제 SQL을 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)`로 분석한 기록이다. 이 문서의 결과는
Issue #73(인덱스 설계 트레이드오프 문서화)의 근거 자료로 사용된다.

## 테스트 환경

- PostgreSQL 16 (`docker compose up -d postgres`, 로컬 1개 인스턴스)
- 애플리케이션 스키마: `spring.jpa.hibernate.ddl-auto=update`로 생성된 실제 런타임 스키마
  (Hibernate가 관리 — `docs/db/ddl.sql`과는 별개, 아래 "발견 1" 참고)
- 데이터 규모: `performances` 20,000행, `performance_schedules` 39,784행
  (공연당 1~3개 회차, `genre`/`region`은 각 enum 값에 균등 분포, `performance_datetime`은
  현재 시각 기준 0~120일 뒤 범위에 균등 분포, 고정 시드(42)로 생성)
- 측정 방법: 각 쿼리를 인덱스 적용 전/후로 한 번씩 `EXPLAIN (ANALYZE, BUFFERS)` 실행
  (반복 측정에 의한 평균/분산은 구하지 않음 — 한계 항목 참고)

## 발견 1 — 현재 런타임 스키마에는 `docs/db/ddl.sql`의 인덱스가 없다

`docs/db/ddl.sql`은 `performances(genre)`, `performances(region)`,
`performance_schedules(performance_id)`, `performance_schedules(performance_datetime)`,
`performance_schedules(status)`에 인덱스를 정의하고 있지만, 두 엔티티 모두 `@Table(indexes = ...)`
선언이 없어 `ddl-auto=update`가 관리하는 실제 스키마에는 **기본 키 인덱스만 존재하고 위 5개
인덱스는 전혀 적용되어 있지 않다** (`\d performances`, `\d performance_schedules`로 직접 확인).

즉 `ddl.sql`은 설계 문서일 뿐 런타임에 자동으로 반영되지 않는다. 이 분석은 그래서 두 단계로
나눠 진행했다.

- **BEFORE**: 현재 런타임 그대로(인덱스 없음, PK만 존재)
- **AFTER**: `ddl.sql`에 문서화된 5개 인덱스를 수동으로 추가한 뒤 재측정

## 대표 검색 쿼리 5개 (실제 QueryDSL 생성 SQL)

Hibernate `show-sql`로 캡처한, `PerformanceRepositoryImpl.search()`가 실제로 생성하는 SQL이다
(파라미터는 `?`).

**Q1. 장르만 (CONCERT)**
```sql
select p1_0.* from performances p1_0
where p1_0.genre=?
order by p1_0.created_at desc offset ? rows fetch first ? rows only
```

**Q2. 지역만 (SEOUL)**
```sql
select p1_0.* from performances p1_0
where p1_0.region=?
order by p1_0.created_at desc offset ? rows fetch first ? rows only
```

**Q3. 날짜 기간만**
```sql
select p1_0.* from performances p1_0
where exists (
    select 1 from performance_schedules ps1_0
    where ps1_0.performance_id=p1_0.performance_id
      and (ps1_0.performance_datetime>=? and ps1_0.performance_datetime<=?)
)
order by p1_0.created_at desc offset ? rows fetch first ? rows only
```

**Q4. 장르 + 지역**
```sql
select p1_0.* from performances p1_0
where p1_0.genre=? and p1_0.region=?
order by p1_0.created_at desc offset ? rows fetch first ? rows only
```

**Q5. 장르 + 지역 + 날짜**
```sql
select p1_0.* from performances p1_0
where p1_0.genre=? and p1_0.region=?
  and exists (
    select 1 from performance_schedules ps1_0
    where ps1_0.performance_id=p1_0.performance_id
      and (ps1_0.performance_datetime>=? and ps1_0.performance_datetime<=?)
)
order by p1_0.created_at desc offset ? rows fetch first ? rows only
```

날짜 조건이 있는 Q3/Q5에서 주목할 점: JPQL의 `exists` 서브쿼리가 PostgreSQL 실행계획에서는
그대로 서브플랜으로 실행되지 않고 **`Hash Semi Join`으로 평탄화(flatten)**된다. `exists`로 작성했다고
해서 항상 상관 서브쿼리로 실행되는 것은 아니라는 뜻이다.

## 실행계획 비교 (BEFORE → AFTER)

바인드 값: `genre=CONCERT`, `region=SEOUL`, 날짜 범위 = `now()+10일 ~ now()+40일`, `LIMIT 20`.

| 쿼리 | BEFORE (인덱스 없음) | AFTER (ddl.sql 인덱스 적용) | Execution Time |
| --- | --- | --- | --- |
| Q1 장르만 | `Seq Scan` on performances (20,000행 스캔, 3,312행 필터 통과) | `Bitmap Index Scan` on `performances_genre_idx` | 3.29ms → 1.22ms |
| Q2 지역만 | `Seq Scan` (20,000행 스캔, 1,549행 통과) | `Bitmap Index Scan` on `performances_region_idx` | 1.53ms → 1.06ms |
| Q3 날짜만 | `Hash Semi Join` + 양쪽 `Seq Scan` (schedules 39,784행 스캔) | `Hash Semi Join`, schedules 쪽만 `Bitmap Index Scan` on `performances_datetime_idx`, performances 쪽은 여전히 `Seq Scan`(조건 없음) | 16.43ms → 11.75ms |
| Q4 장르+지역 | `Seq Scan` (20,000행 스캔, 258행 통과) | `Bitmap Index Scan` on `performances_region_idx`만 사용, `genre`는 `Filter`로 처리 (아래 "발견 2") | 1.37ms → 0.91ms |
| Q5 장르+지역+날짜 | `Hash Semi Join` + 양쪽 `Seq Scan` | performances 쪽은 Q4와 동일(`region` 인덱스 + `genre` Filter), schedules 쪽은 `performance_datetime` 인덱스 사용 | 10.87ms → 5.13ms |

전체 EXPLAIN 원본 출력은 `docs/perf/raw/explain-before.txt`, `docs/perf/raw/explain-after.txt`에 보관했다.

## 발견 2 — 단일 컬럼 인덱스 2개는 조합되지 않고 하나만 선택된다

Q4(장르+지역)에서 인덱스를 둘 다 추가했음에도 플래너는 `region` 인덱스만으로 Bitmap Scan을 하고
`genre` 조건은 그 결과에 대한 `Filter`로 처리했다 (`Recheck Cond: region`, `Filter: genre`).
PostgreSQL은 여러 단일 컬럼 인덱스를 `BitmapAnd`로 결합할 수 있지만, 이 데이터 분포·규모에서는
플래너가 "지역 인덱스만 타고 나머지는 필터링"이 더 싸다고 판단한 것이다.

즉 **`performances(genre)` + `performances(region)` 단일 인덱스 2개를 각각 추가하는 것만으로는
genre+region 복합 조건에서 두 인덱스를 함께 활용하지 못한다.** 이 결과는 Issue #73에서
`(genre, region)` 복합 인덱스 후보를 검토할 때 근거로 쓸 수 있다 — 다만 복합 인덱스의 컬럼 순서·
선택도 비교는 이번 이슈 범위가 아니라 #73에서 다룬다.

또한 `performance_schedules(performance_id)`, `performance_schedules(status)` 인덱스는 이번
대표 쿼리 5개 어디에서도 사용되지 않았다. 검색 쿼리는 `status`로 필터링하지 않고,
`performance_id` 조인은 Hash Semi Join으로 처리되어 인덱스 스캔이 필요 없었기 때문이다
(이 두 인덱스가 다른 접근 경로, 예: 공연 오픈 스케줄러 Issue #75의 회차 조회에서 쓰이는지는
이번 분석 범위 밖).

## 추정 행 수 vs 실제 행 수

`EXPLAIN ANALYZE` 직전에 `ANALYZE`를 수행했고 테스트 데이터가 각 enum 값에 균등 분포하도록
생성됐기 때문에, 모든 쿼리에서 추정 행 수와 실제 행 수가 오차 1% 이내로 거의 일치했다
(예: Q1 추정 3,312 vs 실제 3,312, Q4 추정 1,549(region 인덱스 단계) vs 실제 1,549).
이는 통계가 최신이고 분포가 균일한 이상적인 상황이라는 뜻이며, 실제 프로덕션처럼 인기
공연에 조회가 쏠리는 비균일 분포에서는 추정-실제 오차가 이보다 클 수 있다.

## 한계

- 반복 측정 없이 각 쿼리를 1회씩만 실행했다. 절대 수치(ms)는 참고용이며, 이 문서에서 의미
  있게 보는 것은 실행계획의 **Scan 방식 변화**(Seq → Bitmap Index)와 **인덱스 선택 여부**다.
- 데이터가 균등 분포로 합성 생성되어, 실제 서비스에서 특정 인기 공연·장르에 조회가 쏠리는
  분포와는 다르다.
- 20,000/39,784행은 초기 서비스 규모를 가정한 값이며, 이보다 훨씬 큰 규모(수십만 행 이상)나
  동시 조회 부하 상황에서의 실행계획은 별도로 확인이 필요하다(테스트 예정, 이번 이슈 범위 아님).
- `(genre, region)` 등 복합 인덱스 후보 자체의 성능은 이번 이슈에서 측정하지 않았다 — Issue #73에서
  다룬다.

## Issue #73에 전달하는 인덱스 판단 근거 요약

1. `docs/db/ddl.sql`에 문서화된 인덱스가 실제 런타임 스키마에는 반영되어 있지 않다 —
   먼저 이 갭 자체를 어떻게 해소할지(엔티티에 `@Table(indexes=...)` 추가 등)가 #73의 논의 대상이다.
2. 단일 컬럼 인덱스는 각 조건을 단독으로 걸 때는 확실히 Seq Scan을 Index Scan으로 바꿔준다
   (Q1, Q2 확인).
3. 하지만 genre+region처럼 여러 조건을 동시에 거는 경우, 단일 컬럼 인덱스 2개만으로는 플래너가
   하나만 선택하고 나머지는 Filter로 처리한다 — 복합 인덱스가 필요한지 여부를 #73에서 이 데이터를
   근거로 판단해야 한다.
4. 날짜 범위 조건(`performance_datetime`)은 EXISTS가 Hash Semi Join으로 평탄화되므로,
   `performance_schedules` 쪽 인덱스 효과는 조인이 아니라 서브플랜(schedules 자체 필터링) 단계에서만
   나타난다.
