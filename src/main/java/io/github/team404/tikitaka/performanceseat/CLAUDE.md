# Performance/Seat 도메인 — 담당자 컨텍스트 (신선우)

> 이 파일은 루트 `CLAUDE.md`를 대체하지 않고 보완합니다. `performanceseat` 패키지 루트에 두면
> Claude Code가 이 안에서 작업할 때 자동으로 함께 읽습니다.
>
> 브랜치 네이밍, 라벨 체계, 주차별 전체 일정은 여기 다시 적지 않습니다 — 각각
> `docs/BRANCH_STRATEGY.md`, `docs/LABEL_SYSTEM.md`, `docs/ROADMAP.md`가 원본이니
> 이슈 작업 전에 그 문서들을 참고하세요 (이 문서는 performanceseat 도메인 고유 규칙만 다룹니다).

## 담당 범위
- 공연·좌석(Performance/Seat) 도메인 — 엔티티, 공연 CRUD API
- 대기열 (Redis Sorted Set) + SSE 순번 푸시
- 공연 오픈 스케줄러 (오픈 시간 → 좌석 AVAILABLE 전환 + 대기열 입장 허용)
- Elasticsearch 검색 인덱싱 + QueryDSL 복합 쿼리, 캐시 히트율 튜닝

## 이 도메인에서 반드시 지킬 규칙
- 좌석 상태 전이는 `AVAILABLE → HELD(hold, 5분 TTL) → RESERVED(confirm, 결제 확정)`,
  `HELD`/`RESERVED` 상태에서만 `release()`로 `AVAILABLE`로 되돌릴 수 있다 (`Seat` 엔티티 참고).
  이 전이 로직을 건드리는 코드는 반드시 `SeatTest`류 단위 테스트를 동반해야 함.
- `AVAILABLE → HELD` 전이(분산락 통합 포함)와 예매 레코드 생성은 booking 도메인
  (`ReservationService`) 소관 — 이 패키지에서 예매 트랜잭션 로직을 만들지 말 것.
- 공연/회차/구역/좌석은 서로 다른 애그리거트로 취급하고 FK는 연관관계가 아닌 순수 `Long` id로만
  보관한다 (`Reservation`/`ReservationSeat`/`Seat`와 동일 원칙, 결합도를 낮추기 위함).
- 좌석 배치는 공연장 타입별 자동 템플릿이 아니라 등록 요청에 구역/좌석열을 명시적으로 받아 생성한다
  (이슈 15 결정 — 자동 배치 템플릿은 향후 별도 이슈로 검토).

## 테스트 기준
- 엔티티 상태 전이(`Seat`, `Performance`)는 given/when/then 단위 테스트로 정상/예외 케이스 모두 검증.
- 서비스 레이어는 Mockito 기반 단위 테스트(`ReservationServiceTest` 패턴 참고).
- 대기열(Redis Sorted Set)·캐시 관련 코드는 3~4주차부터 Testcontainers 기반 통합 테스트 필요.

## 이슈 작업 시 기본 지시
1. `gh issue view {번호}`로 이슈 확인 후 구현 범위를 요약해서 먼저 보여줄 것
2. 현재 주차가 뭔지 애매하면 `docs/ROADMAP.md`에서 해당 이슈가 속한 주차를 찾아서 그 주의
   다른 담당자 작업과 경계가 겹치지 않는지 확인
3. `docs/LABEL_SYSTEM.md`의 `trade-off-doc` 라벨이 붙은 이슈(★ 표시 주차)는 구현과 별개로
   트레이드오프 문서 초안도 같이 작성. 실제 코드/구현 근거로만 작성하고 벤치마크 수치를 지어내지
   말 것 — 실측치가 없으면 "테스트 예정"이라고 명시
4. `docs/db/ddl.sql`(확정 ERD)을 수정하는 경우 PR 본문에 변경 이유를 명시하고, 다른 도메인
   Repository/Service에 영향이 가는 변경(예: booking의 `ReservationRepository`에 조회 메서드 추가)은
   PR 본문에 별도로 표시해 리뷰어가 확인할 수 있게 할 것
