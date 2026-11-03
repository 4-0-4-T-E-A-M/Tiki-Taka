# 예매 이벤트 전달에 Kafka를 쓰는 이유 — vs Redis pub/sub, Redis Streams

관련 이슈: #114 (★ Kafka vs Redis pub/sub 비교 문서, 노주희)
전제:
- #92 — 예매 완료 이벤트 Producer (`KafkaEventProducer`, afterCommit 발행)
- #115 — Producer 멱등성·acks·재시도 설정 (`KafkaProducerReliabilityConfig`)
- [[why-redis-lock]] (#36) — Redis는 이미 락·대기열·캐시·랭킹으로 운영 중이라는 전제

이 문서가 다루지 않는 것:
- Consumer 실패 처리·재시도 → #119, DLQ 설계 → #120, 멱등성·파티셔닝 전략 상세 → #121 (조준형)
- DB → ES 동기화(`performance-events`) 전략 → [[db-es-sync-strategy]] (#89)

# 1. 지금 이벤트가 실제로 하는 일

예매가 커밋되면 `ReservationService`가 afterCommit에서 `ReservationEvent`를 `reservation-events` 토픽에
한 번 발행한다(키 = `reservationId`). 같은 토픽을 **서로 다른 Consumer 그룹 3개**가 각자 독립적으로 읽는다.

| Consumer 그룹 | 클래스 | 하는 일 | 이벤트를 놓치면 |
| --- | --- | --- | --- |
| `reservation-event-consumer` | `KafkaEventConsumer` → `ReservationStatisticsService` | 회차·상태별 예매 통계 집계 (`eventId`로 `ProcessedEvent` 중복 제거) | 연습 세션 리포트의 집계 수치가 틀어진다 |
| `email-notification-consumer` | `EmailNotificationConsumer` | `PENDING_PAYMENT` 이벤트 → 예약 완료 메일 발송 | 사용자가 메일을 못 받는다 |
| `popular-performance-ranking` | `ReservationRankingConsumer` | `CONFIRMED` 이벤트 → 인기 공연 점수 `ZINCRBY` | 랭킹 점수가 덜 오른다 (근사치라 허용 범위) |

이 구조에서 메시징 수단에 요구되는 것:

1. **fan-out**: 이벤트 1건이 3개 Consumer 모두에게 간다. Consumer를 추가해도 Producer는 바뀌지 않는다.
2. **Consumer가 잠시 죽어도 이벤트가 남아 있어야 한다.** 메일 서버 장애로 메일 Consumer가 재시작하는
   동안 들어온 예매 이벤트가 사라지면, 그 사용자들은 메일을 영영 못 받는다. 통계도 마찬가지.
3. **같은 예매의 이벤트는 순서대로**: 이후 결제(`CONFIRMED`)·취소(`CANCELLED`) 이벤트가 붙으면
   "생성 → 확정 → 취소" 순서가 Consumer에서 뒤집히면 안 된다.
4. **재처리 가능**: Consumer 버그를 고친 뒤, 혹은 새 Consumer를 붙였을 때 과거 이벤트를 다시 읽을 수 있으면 좋다.
5. **처리량**: 오픈 순간 k6 봇이 같은 회차에 몰린다. 단, 이벤트는 **예매가 성공한 건에만** 나가므로
   좌석 수(회차당 수백~수천)가 상한이다. 락 경합에서 진 요청은 이벤트를 만들지 않는다.

1·2·4가 핵심이다. 처리량(5)은 세 후보 모두 이 규모에서는 충분하다.

# 2. 후보 3가지

## A. Redis pub/sub

`PUBLISH reservation-events {json}` → `SUBSCRIBE`한 클라이언트에게 즉시 전달.

- **fire-and-forget**: 메시지를 저장하지 않는다. 발행 순간 구독 중인 클라이언트에게만 가고 끝이다.
  Consumer가 재시작 중이면 그 사이 메시지는 **그대로 유실**된다(요구 2 불충족).
- ACK 개념이 없다. Consumer가 받았다가 처리 중 예외가 나도 다시 받을 방법이 없다.
- 재처리 불가(요구 4 불충족). 새 Consumer는 "지금부터" 메시지만 받는다.
- fan-out은 자연스럽다 — 구독자 전원에게 브로드캐스트(요구 1 충족).
- 지연이 매우 짧고, Redis는 이미 운영 중이라 **추가 인프라가 없다**.

→ 실시간 알림 "신호"(예: 대기열 순번 변경을 여러 서버 인스턴스의 SSE 연결로 뿌리기)에는 맞지만,
   **"놓치면 안 되는 사실"을 전달하는 용도에는 맞지 않는다.**

## B. Redis Streams

`XADD reservation-events * ...` → `XREADGROUP`으로 Consumer 그룹 단위 소비, `XACK`로 확인.

- 메시지가 Stream에 **저장**된다. Consumer 그룹별로 마지막 읽은 ID를 서버가 관리하고,
  ACK 안 된 메시지는 PEL(Pending Entries List)에 남아 `XAUTOCLAIM`으로 다시 가져올 수 있다(요구 2 충족).
- Consumer 그룹을 여러 개 만들면 그룹마다 독립적으로 전체 메시지를 읽는다(요구 1 충족).
- ID 범위로 과거 메시지를 다시 읽을 수 있다(요구 4 대체로 충족). 단, 메모리 기반이라 `MAXLEN`으로
  길이를 잘라야 하고, 잘린 이후 이벤트는 재처리 불가.
- 한계:
  - **영속성이 Redis 설정에 달려 있다.** 이 프로젝트의 Redis는 락·대기열·캐시용이라 "날아가도 다시
    만들 수 있는" 데이터 기준으로 운영한다. AOF `everysec`를 켜도 장애 시 최대 1초분 유실 가능,
    RDB만이면 스냅샷 이후 전부 유실. 이벤트 로그를 같은 Redis에 두면 **캐시 수준의 내구성으로
    이벤트 원장을 관리**하게 된다.
  - **메모리 경합**: 오픈 순간에는 분산 락·대기열 ZSET·SSE 상태가 같은 Redis에 몰린다. 이벤트
    로그까지 같은 메모리를 쓰면 eviction 정책과 충돌할 수 있다(`allkeys-lru`면 Stream이 축출될 수 있음).
  - 파티션 개념이 없다. 한 Stream은 한 노드에 있고, 확장하려면 키를 직접 샤딩해야 한다.
    같은 예매의 순서는 단일 Stream이라 지켜지지만, 그룹 내 Consumer 여러 개가 나눠 받으면
    **같은 `reservationId`가 다른 Consumer로 갈 수 있어** 순서 보장은 앱이 해야 한다(요구 3 부분 충족).
  - Spring 지원(`StreamMessageListenerContainer`)이 spring-kafka만큼 성숙하지 않다. 재시도·DLQ·
    오프셋 관리 같은 것을 직접 짜야 한다(#119·#120 작업량이 커짐).

## C. Kafka (선택)

`reservation-events` 토픽에 키(`reservationId`)와 함께 발행, Consumer 그룹 3개가 각자 offset 관리.

- **디스크 로그에 보존**(기본 7일). Consumer가 죽었다 살아나면 커밋된 offset부터 다시 읽는다(요구 2 충족).
- Consumer 그룹마다 독립적으로 전체 이벤트를 읽는다(요구 1 충족). 그룹 추가가 Producer에 영향 없음.
- offset을 되돌리면 보존 기간 안의 과거 이벤트를 재처리할 수 있다(요구 4 충족). 새 Consumer 그룹은
  `auto-offset-reset=earliest`로 처음부터 읽을 수 있다.
- **키 기반 파티셔닝**: 같은 `reservationId`는 항상 같은 파티션 → 파티션 안에서는 순서 보장,
  그룹 내 Consumer가 늘어도 한 파티션은 한 Consumer만 읽는다(요구 3 충족). #115의 멱등 Producer
  (`max.in.flight ≤ 5`)는 재시도 중에도 이 순서를 유지한다.
- Producer 측 `acks=all` + 멱등성으로 브로커 저장까지의 유실·중복을 막는다(#115).
- spring-kafka가 재시도·`DefaultErrorHandler`·DLT(Dead Letter Topic)를 제공해 #119·#120을 설정
  위주로 풀 수 있다.
- 한계:
  - **운영할 인프라가 하나 더 생긴다.** 브로커(KRaft) 설정, 토픽·파티션 관리, Docker Compose 항목,
    로컬/테스트 환경(EmbeddedKafka) 구성. 실제로 이번 작업 중에도 "Kafka가 안 떠 있으면 예매 요청
    스레드가 `max.block.ms`(기본 60초) 동안 막히는" 문제를 겪었다(#115에서 2초로 제한).
  - 직렬화 계약을 관리해야 한다 — `ReservationEvent`의 스키마 버전 정책(필드 추가만 허용, 깨지는 변경은
    새 타입·새 토픽)을 따로 정해둔 이유다.
  - 지연은 Redis pub/sub보다 길다(배치·`acks=all`). 다만 이벤트는 afterCommit에서 비동기로 나가므로
    **예매 API 응답 시간에는 더해지지 않는다.**

# 3. 비교표

| 기준 | A. Redis pub/sub | B. Redis Streams | **C. Kafka** |
| --- | --- | --- | --- |
| 메시지 영속성 | ❌ 저장 안 함 | △ 메모리 + AOF/RDB 설정에 의존 | ✅ 디스크 로그, 보존 기간 동안 유지 |
| Consumer 오프라인 시 | ❌ 그 사이 메시지 유실 | ✅ 그룹별 last-id + PEL로 이어서 읽음 | ✅ 그룹별 커밋 offset부터 이어서 읽음 |
| 처리 실패 시 재전달 | ❌ ACK 없음 | ✅ PEL + `XAUTOCLAIM` (직접 구현) | ✅ offset 미커밋 → 재전달, spring-kafka 재시도·DLT |
| 과거 이벤트 재처리 | ❌ | △ `MAXLEN` 이내만 | ✅ 보존 기간 이내, offset 리셋 |
| fan-out (Consumer 3개) | ✅ 브로드캐스트 | ✅ Consumer 그룹 | ✅ Consumer 그룹 |
| 같은 예매의 순서 보장 | △ 단일 채널 순서뿐, 유실 시 의미 없음 | △ 단일 Stream 순서, 그룹 내 분배 시 앱이 보장 | ✅ 키 → 파티션 고정, 파티션 내 순서 |
| Producer 유실·중복 방지 | ❌ | △ `XADD` 응답만 확인 | ✅ `acks=all` + 멱등 Producer |
| 처리량 (이 프로젝트 규모) | ✅ 충분 | ✅ 충분 | ✅ 충분 |
| 지연 | ✅ 가장 짧음 | ✅ 짧음 | △ 상대적으로 김 (비동기라 API 영향 없음) |
| 추가 인프라 | ✅ 없음 (기존 Redis) | ✅ 없음 (기존 Redis) | ❌ 브로커 운영 필요 |
| 기존 Redis에 주는 영향 | 작음 | ❌ 오픈 순간 락·대기열과 메모리 경합 | 없음 (분리) |
| Spring 지원 성숙도 | 보통 | △ 재시도·DLQ 직접 구현 | ✅ spring-kafka |

# 4. 판단 기준과 결론

**판단 기준: "Consumer가 잠시 죽어도 예매 사실이 사라지지 않는가"를 최우선으로 둔다.**

이 프로젝트에서 예매 이벤트는 신호가 아니라 **사실의 기록**이다. 메일 발송, 연습 세션 리포트의 통계는
"예매가 일어났다"는 사실을 빠짐없이 받아야 의미가 있다. Redis pub/sub은 이 기준을 구조적으로 충족하지
못해 탈락한다.

Redis Streams는 기능적으로는 요구를 대부분 채운다. 그럼에도 Kafka를 고른 이유:

1. **내구성 수준을 분리하고 싶다.** Redis는 "날아가도 다시 만들 수 있는" 데이터(락·대기열·캐시·근사 랭킹)
   용도로 운영한다. 이벤트 원장을 같은 Redis에 넣으면 그 원장도 캐시 수준의 내구성을 갖게 된다.
2. **장애 지점을 분리하고 싶다.** 오픈 순간 Redis는 분산 락·대기열로 가장 바쁘다. 이벤트 로그가 같은
   메모리·같은 인스턴스를 쓰면, 이벤트가 몰릴 때 락 지연이 늘거나 반대로 락 트래픽 때문에 이벤트 쓰기가
   밀린다. 이 프로젝트의 핵심 지표(락 경합, p95)를 오염시킨다.
3. **순서·재시도·DLQ를 직접 짜지 않아도 된다.** 키 기반 파티셔닝과 spring-kafka 에러 핸들러로
   #119·#120·#121을 설정과 소량의 코드로 해결할 수 있다.
4. 학습·포트폴리오 측면에서도, "fan-out + 재처리 가능한 이벤트 로그"라는 Kafka의 본래 용도에 맞게 쓴 사례다.

Kafka 도입 비용(브로커 운영, KRaft 설정, 직렬화 계약)은 Docker Compose 단일 브로커로 로컬 비용을 낮췄고,
Producer 쪽 장애 영향은 #115에서 `max.block.ms` 2초로 묶어 예매 API에 번지지 않게 했다.

## 남아 있는 위험 (Kafka를 써도 해결되지 않는 것)

- **커밋 후 발행 전 크래시 / 브로커 장기 장애 시 유실**: afterCommit 발행이라 DB 커밋과 Kafka 발행이
  원자적이지 않다. 이를 막으려면 Transactional Outbox(이벤트를 같은 트랜잭션으로 DB에 쓰고 별도 릴레이가
  발행)가 필요하다. 현재는 #115 방침대로 최종 실패 시 이벤트 전체를 로그로 남겨 수동 재발행한다.
  Outbox 도입 조건은 아래 5절 참고.
- **Consumer 측 중복**: Kafka는 기본적으로 at-least-once 전달이다. 통계 Consumer는 `eventId`로 중복을
  제거하지만, **메일 Consumer와 랭킹 Consumer는 현재 중복 제거가 없어** 재전달 시 메일이 두 번 가거나
  점수가 두 번 오를 수 있다. 메일은 사용자에게 보이므로 #119·#121에서 함께 다룰 대상이다.
- (참고) 현재 발행 지점은 예매 생성(`PENDING_PAYMENT`)뿐이라 랭킹 Consumer가 기다리는 `CONFIRMED`
  이벤트는 아직 나가지 않는다. 결제 확정 흐름이 생길 때 같은 토픽·같은 키로 발행하면 된다.

# 5. 선택하지 않은 대안이 더 적합해지는 조건

| 조건 | 더 맞는 선택 |
| --- | --- |
| 유실돼도 괜찮은 실시간 "신호"만 필요할 때 (예: 여러 서버 인스턴스의 SSE 연결에 대기열 순번 변경 브로드캐스트) | **Redis pub/sub** — 저장·재처리가 필요 없고 지연이 가장 짧다 |
| Consumer가 1~2개, 이벤트량이 작고, Kafka 운영 인력·인프라를 둘 수 없을 때 | **Redis Streams** — 단, 이벤트 전용 Redis 인스턴스를 따로 두고 AOF를 켠다는 전제 |
| 이벤트 유실이 금전·법적 문제로 이어질 때 (실제 결제·환불 이벤트) | **Kafka + Transactional Outbox** — 지금 구조에서 발행 경로만 교체 |
| 다중 인스턴스로 확장해 같은 이벤트를 수십 개 서비스가 구독할 때 | **Kafka** 유지, 파티션 수·복제 계수 상향(현재 로컬은 브로커 1대, RF=1) |
