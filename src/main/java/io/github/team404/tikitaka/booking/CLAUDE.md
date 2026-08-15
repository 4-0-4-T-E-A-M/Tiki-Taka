# Booking 도메인 — 담당자 컨텍스트 (노주희)

> 이 파일은 루트 `CLAUDE.md`를 대체하지 않고 보완합니다. `booking` 패키지 루트에 두면
> Claude Code가 이 안에서 작업할 때 자동으로 함께 읽습니다.
>
> 브랜치 네이밍, 라벨 체계, 주차별 전체 일정은 여기 다시 적지 않습니다 — 각각
> `docs/BRANCH_STRATEGY.md`, `docs/LABEL_SYSTEM.md`, `docs/ROADMAP.md`가 원본이니
> 이슈 작업 전에 그 문서들을 참고하세요 (이 문서는 booking 도메인 고유 규칙만 다룹니다).

## 담당 범위
- 예매(Booking) 도메인 — 엔티티, 예매 생성·조회·취소 API
- Redis 분산 락(Redisson) — 좌석 선점 동시성 제어
- Kafka **Producer** — 예매 완료 이벤트 발행 (Consumer는 조준형 담당, 여기서 건드리지 않음)

## 이 도메인에서 반드시 지킬 규칙
- 좌석 상태 전이는 `AVAILABLE → RESERVED(5분 TTL) → CONFIRMED/PAID`, 실패 시 다시 `AVAILABLE`.
  이 전이 로직을 건드리는 코드는 반드시 동시성 테스트를 동반해야 함(아래 참고).
- 좌석 선점 시 분산락(`seat-lock:{seatId}`) 획득 실패는 **대기시키지 않고 즉시 실패 반환**
  (대기/재시도는 신선우 담당 대기열 도메인 소관 — 이 패키지에서 대기 로직을 만들지 말 것).
- 5분 TTL 만료로 락이 자동 해제되게 하고, 별도 스케줄러로 수동 해제 로직을 중복 구현하지 말 것.
- Kafka 이벤트 발행 실패가 예매 트랜잭션 자체를 롤백시키면 안 됨(Producer는 예매 확정 이후
  decoupled 단계 — ROADMAP 7주차 "Kafka vs Redis pub/sub" 문서화 이유이기도 함).

## 테스트 기준 (이 도메인은 특히 엄격하게)
- 분산 락/트랜잭션 관련 코드는 Mock이 아니라 실제 Redis(Testcontainers) 기반 동시성 테스트 필수.
- 기준 시나리오: 동일 좌석에 N명(최소 100명) 동시 요청 → 성공 정확히 1건, 나머지는 실패 응답.
- 결제 실패/타임아웃 시 좌석이 `AVAILABLE`로 복구되는지 별도 케이스로 검증.

## 이슈 작업 시 기본 지시
1. `gh issue view {번호}`로 이슈 확인 후 구현 범위를 요약해서 먼저 보여줄 것
2. 현재 주차가 뭔지 애매하면 `docs/ROADMAP.md`에서 해당 이슈가 속한 주차를 찾아서 그 주의
   다른 담당자 작업과 경계가 겹치지 않는지 확인
3. Redisson/트랜잭션 관련 변경은 Testcontainers 기반 동시성 테스트를 같이 작성 (누락 시 반려)
4. `docs/LABEL_SYSTEM.md`의 `trade-off-doc` 라벨이 붙은 이슈(★ 표시 주차)는 구현과 별개로
   트레이드오프 문서 초안도 같이 작성. 이때:
  - 실제 코드/구현 근거로만 작성하고, 벤치마크 수치나 비교 결과를 지어내지 말 것 —
    실측치가 없으면 "테스트 예정"이라고 명시
  - 비교 대상은 이 프로젝트의 실제 제약(다중 인스턴스 확장, DB 커넥션 풀 부담, TTL 기반
    자동 해제 필요성 등) 기준으로 판단하고, 일반론적인 장단점 나열로 채우지 말 것
  - 실제 겪었던 문제나 의사결정 히스토리(예: 처음엔 다른 방식을 썼다가 바꾼 이유)가 있다면
    프롬프트에 그 사실관계를 먼저 알려줄 것 — 코드만 봐서는 알 수 없는 부분
5. Kafka 이벤트 스키마를 바꾸는 경우에만 Consumer 담당자(조준형)에게 공유 — 그 외 Consumer
   로직에는 손대지 말 것

## 작업 로그 (PR 올리기 전 임시 정리)

### 이슈 #34 — Redisson 분산 락 구현 (PR #46, `develop` 머지 완료)
- 브랜치: `feature/34-feat-redisson-분산락-구현`. 처음엔 `develop`에 아직 없던 #33 인프라
  때문에 `feature/33` 위에서 분기했다가, #33이 `develop`에 머지된 뒤 `git rebase --onto
  origin/develop feature/33-... feature/34-...`로 `#34` 커밋 하나만 `develop` 최신 위로 옮김
  (`build.gradle`에서 develop 쪽 OAuth 관련 의존성과 충돌 나서 수동 병합함).
- 변경 파일: `build.gradle`(redisson 3.40.2 + testcontainers 테스트 의존성),
  `application.yaml`(`spring.data.redis.host/port`), `booking/config/RedissonConfig.java`(신규),
  `ReservationService.java`(락 통합), `ReservationServiceTest.java`(RLock mock 추가),
  `ReservationConcurrencyTest.java`(신규, Testcontainers 기반),
  `src/test/resources/application.yaml`(테스트용 `spring.data.redis.host/port` 추가)
- 주요 설계 결정
  - 락 키 `seat-lock:{seatId}`, `waitTime=0`(즉시 실패) / `leaseTime=3초`
  - 좌석 여러 개 요청 시 seatId 정렬 후 순서대로 락 획득(데드락 방지)
  - 락 해제는 `finally`가 아니라 `TransactionSynchronizationManager`의 `afterCompletion`에 등록 —
    `@Transactional` 프록시의 실제 커밋이 메서드 리턴 *이후*에 일어나므로, 단순 `finally` 언락은
    "커밋 전에 락이 풀리는" 레이스를 만들어 #35가 요구하는 "성공 정확히 1건"이 깨질 수 있음.
    관리되는 트랜잭션이 없는 호출(단위 테스트 등)에서는 즉시 해제로 폴백
  - 락 실패 응답은 기존 스타일대로 커스텀 예외 없이 `ResponseStatusException(CONFLICT)`
- 테스트 범위 분담: 이번 이슈에는 "2스레드 동시 요청 → 성공 1건/충돌 1건" 최소 검증만 포함.
  100명 규모 시나리오·최종 DB 상태 정합성·실패 응답 폭넓은 케이스는 이슈 #35에서 이어감
- 검증: `ReservationServiceTest`, `ReservationConcurrencyTest`, 베이스라인 `TikitakaApplicationTests`
  모두 `./gradlew test` 통과 (로컬에 `docker compose up -d`로 postgres/redis 필요)

### 발견한 기존 버그 → `src/test/resources/application.yaml`에 테스트용 기본값 추가로 해결
`me.paulschwarz:springboot3-dotenv`가 `developmentOnly`라 `testRuntimeClasspath`에 없어서
`./gradlew test` 실행 시 `.env`가 로드되지 않는 기존 버그가 있었음(`feature/33` 베이스에서도
재현, 이번 PR 이전부터 있던 문제). 처음엔 `testRuntimeOnly`에도 dotenv를 추가해서 해결했었는데,
rebase 과정에서 develop에 이미 `src/test/resources/application.yaml`(H2로 datasource 오버라이드)이
추가된 걸 확인하고 그 패턴에 맞춰 dotenv 의존성 추가 대신 같은 파일에 `spring.data.redis.host/port`
기본값(`localhost`/`6379`)을 넣는 쪽으로 다시 바꿈 — 테스트가 dotenv/env var에 전혀 기대지 않고
자체 설정으로 완결되는 게 더 일관적이라고 판단.

### 이슈 #35 — 동시 예매 시나리오 테스트 (100명 동시) (PR #47, `develop` 머지 완료)
- 브랜치: `feature/35-test-동시-예매-시나리오-테스트` (`#34` PR #46이 아직 미머지라 `feature/34`
  위에서 분기 — `#34`가 머지되면 `git rebase --onto origin/develop feature/34-... feature/35-...`
  로 옮길 것, `#34`→develop rebase 때와 동일한 절차)
- 변경 파일: `ReservationConcurrencyTest.java`에 100-스레드 테스트 메서드 추가 (새 파일 안 만들고
  #34에서 만든 파일 재사용 — static Redis 컨테이너/빈 재사용, 클래스 상단 주석도 갱신)
- #34에서 이미 정한 설계(즉시 실패 waitTime=0, 재시도는 대기열 도메인 소관)를 그대로 두고,
  100명 규모에서도 성립하는지만 검증. 새로운 설계 판단은 없었음
- 검증 항목: 성공 1건/충돌(409) 99건, 그 외 형태의 실패 0건, 테스트 종료 후 `Seat.status`가
  `HELD` 1건(이슈 원문은 "RESERVED"라고 적혀 있지만 실제 코드의 임시 홀드 상태명은 `HELD`),
  `ReservationSeat` 매핑 1건
- 검증: `./gradlew test --tests "*.ReservationConcurrencyTest"`, 전체 `./gradlew test` 모두 통과

### #34/#35 PR 머지 (2026-08-11) — Week 4 이슈 #52 착수 전 정리
`#34`(PR #46)·`#35`(PR #47)가 Week 3에 구현 완료된 채로 리뷰만 받고 `develop`에 오래 안
올라가 있었는데, 그 사이 `develop`이 대기열 기능(#49) 머지로 한참 앞서 나가 있었음. #52
작업을 시작하기 전에 먼저 두 PR을 `develop` 기준으로 정리해서 머지:
- `feature/34-...`를 `origin/develop`로 rebase. `build.gradle`에 테스트용 testcontainers
  의존성이 양쪽에서 추가되어 충돌(develop 쪽은 대기열 도메인 테스트용으로 이미
  `testcontainers-bom`+`testcontainers`+`junit-jupiter` 조합을 추가해둔 상태, `feature/34`
  쪽은 `spring-boot-testcontainers`를 추가했었음) — `ReservationConcurrencyTest`가
  `@ServiceConnection` 없이 `GenericContainer`+`@Testcontainers`만 쓰므로
  `spring-boot-testcontainers`는 불필요, develop 쪽 조합만 남기고 해결
- rebase 후 PR #46 스쿼시 머지 → `develop`
- `feature/35-...`를 `git rebase --onto origin/develop bea486e feature/35-...`로 옮겨서
  `#34`와 겹치는 커밋은 버리고 100-스레드 테스트 커밋만 새 `develop` 위로 이식 (충돌 없음)
- PR #47 base를 `feature/34-...` → `develop`로 재지정 후 스쿼시 머지
- 이슈 #34/#35는 머지 시 자동 클로즈됨

### 이슈 #52 — 예매 트랜잭션 고도화 (구현 완료)
- 브랜치: `feature/52-예매-트랜잭션-고도화-분산락-통합` (위 정리가 끝난 `develop` 기준으로 새로 분기)
- 이슈 본문 체크리스트를 코드와 대조한 결과, "락 획득 순서 고정", "커밋 이후 언락",
  "락 실패 시 즉시 409" 등은 #34/#35에서 이미 구현되어 있었음. 유일하게 비어 있던 항목은
  "락 획득 후 DB 상태 재확인 → 실패 시 즉시 실패 응답": `Seat.hold()`는 상태가 `AVAILABLE`이
  아니면 `IllegalStateException`을 던지는데, `ReservationService.createReservation`에서
  이를 잡지 않아 `GlobalExceptionHandler`의 `Exception` 폴백 핸들러로 떨어져 500으로 응답되고
  있었음 — 락이 정상 동작하는 한 실제로는 거의 발생하지 않는 경로지만, 발생했을 때 스펙대로
  "즉시 실패 응답"이 되도록 고쳐야 하는 갭이었음
- 변경: `ReservationService.createReservation`에서 `seats.forEach(Seat::hold)`를
  `IllegalStateException`을 잡아 `ResponseStatusException(CONFLICT)`로 변환하는 루프로 교체
  (락 실패와 동일하게 커스텀 예외 없이 기존 스타일 유지)
- `ReservationServiceTest`에 좌석을 미리 `HELD`로 만들어두고 락은 정상 획득한 케이스를
  추가해, 재확인 실패가 500이 아니라 409로 응답되는지 검증
- 검증: `./gradlew test --tests "*.ReservationServiceTest" --tests "*.TikitakaApplicationTests"`
  통과 (Docker 미사용 환경이라 `ReservationConcurrencyTest`는 로컬에서 별도로
  `docker compose up -d` 후 전체 `./gradlew test`로 재검증 필요)
