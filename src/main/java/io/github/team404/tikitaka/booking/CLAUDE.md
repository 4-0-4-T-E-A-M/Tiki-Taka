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

### 이슈 #34 — Redisson 분산 락 구현 (구현 완료, PR 미생성)
- 브랜치: `feature/34-feat-redisson-분산락-구현` (`develop`에 아직 없는 #33 인프라에 의존하므로
  `develop`이 아니라 `feature/33-chore-redis-환경-세팅-docker-compose` 위에서 분기함 —
  **#33이 먼저 머지되면 이 브랜치를 `develop`으로 rebase 필요**)
- 변경 파일: `build.gradle`(redisson 3.40.2 + testcontainers 테스트 의존성),
  `application.yaml`(`spring.data.redis.host/port`), `booking/config/RedissonConfig.java`(신규),
  `ReservationService.java`(락 통합), `ReservationServiceTest.java`(RLock mock 추가),
  `ReservationConcurrencyTest.java`(신규, Testcontainers 기반)
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
  모두 `./gradlew test` 통과 (아래 별도 이슈로 인한 env var 수동 주입 필요)

### 발견한 기존 버그 → 이번 브랜치에서 같이 수정함
`me.paulschwarz:springboot3-dotenv`가 `build.gradle`에 `developmentOnly`로만 선언되어 있어
`testRuntimeClasspath`에 포함되지 않았음 → `./gradlew test` 실행 시 `.env`가 로드되지 않아
`${DB_URL}` 등이 미해석 상태로 남고 `'url' must start with "jdbc"`로 실패하던 기존 버그
(`feature/33` 베이스에서도 재현됨, 이번 PR 이전부터 있던 문제).
`build.gradle`에 `testRuntimeOnly 'me.paulschwarz:springboot3-dotenv'` 한 줄 추가로 해결—
이제 env var 수동 주입 없이 `./gradlew test`만으로 `TikitakaApplicationTests`/
`ReservationServiceTest`/`ReservationConcurrencyTest` 모두 통과.
(참고: `bootJar` 실행 시 `developmentOnly` 설정 자체의 별도 resolve 에러는 여전히 남아있음 —
이건 이번 fix와는 다른 원인이라 미해결 상태로 남겨둠, 필요시 별도 확인 필요)
