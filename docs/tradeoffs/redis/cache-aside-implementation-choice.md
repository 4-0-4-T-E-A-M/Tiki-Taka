# Cache-Aside 구현 방식: RedisTemplate 직접 사용 vs @Cacheable

관련 이슈: #56 (Cache-Aside 인기 공연 캐싱 구현)

# 문제 상황

공연 오픈 시점처럼 동일 공연 상세(`GET /api/performances/{id}`)를 다수 사용자가 동시에 조회하는
상황에서 매 요청마다 DB를 타지 않도록 Cache-Aside 패턴을 적용해야 했다(`PerformanceService`).
Spring에서 Cache-Aside를 구현하는 방법은 크게 두 가지다.

- 방식 A: Spring Cache 추상화(`@Cacheable`/`@CachePut`/`@CacheEvict`) + `RedisCacheManager`
- 방식 B: `StringRedisTemplate`을 직접 감싸는 Repository에서 캐시 조회·저장을 명시적으로 호출

# 고려한 대안 비교

| 항목 | A. `@Cacheable` | B. RedisTemplate 직접 사용 |
| --- | --- | --- |
| 코드량 | 어노테이션 한 줄 + `RedisCacheManager` 빈 설정 | Repository 클래스 + 직렬화 코드 필요 |
| 기존 컨벤션과의 일치 | 이 프로젝트에 아직 Spring Cache 추상화가 전혀 설정되어 있지 않음(신규 인프라) | `WaitingQueueRepository`가 이미 같은 패턴(Repository가 `StringRedisTemplate`을 감싸 키를 설계)으로 대기열(Redis Sorted Set)을 구현 중 |
| 캐시 히트/미스 테스트 용이성 | AOP 프록시를 통과해야 캐시가 동작 — 프록시 빈 주입, self-invocation 회피가 필요해 테스트가 간접적 | Repository 메서드(`findDetail`/`saveDetail`) 호출 여부를 Mockito `verify`로 직접 단정 가능 |
| 세밀한 제어 | 캐시 대상 데이터 형식(엔티티 vs DTO), 직렬화 방식을 커스터마이징하려면 `RedisCacheManager`/`RedisSerializer`를 별도로 구성해야 함 | 무엇을 캐싱할지, 어떤 키로 캐싱할지를 코드에서 그대로 읽을 수 있음 |
| self-invocation 위험 | 같은 클래스 내부 메서드 호출 시 `@CacheEvict` 등이 프록시를 우회해 무시될 수 있음(예: `updatePerformance`가 나중에 캐시를 무효화하려 할 때) | 해당 문제 자체가 없음(직접 호출) |
| 학습 비용 | Spring 표준 관용구라 낮음 | Redis 직렬화·키 설계를 직접 다뤄야 해 상대적으로 높음 |

# 최종 선택: RedisTemplate 직접 사용(방식 B)

`PerformanceCacheRepository`가 `StringRedisTemplate` + `ObjectMapper`로 `PerformanceDetailResponse`를
JSON 직렬화해 저장/조회하고, `PerformanceService.getPerformanceDetail()`이 캐시 조회 → 히트 시 반환,
미스 시 DB 조회 후 캐시 채움 흐름을 명시적으로 구현했다.

## 판단 근거

1. **기존 컨벤션과의 일관성** — 같은 도메인의 대기열 기능(`WaitingQueueRepository`)이 이미
   `StringRedisTemplate`을 직접 감싸는 Repository 패턴을 쓰고 있다. `@Cacheable`을 쓰려면
   이 프로젝트에 없던 `RedisCacheManager` 설정(직렬화 방식, 기본 TTL 등)을 새로 추가해야 하는데,
   같은 Redis 캐싱 목적에 두 가지 서로 다른 접근 방식을 병행할 이유가 없었다.
2. **DoD의 "캐시 히트/미스 테스트" 요구와의 궁합** — 이슈 #56의 완료 기준에 캐시 히트/미스 각각의
   동작을 검증하는 테스트가 명시되어 있다. Repository 메서드 호출 여부를 직접 `verify`할 수 있는
   수동 패턴이 AOP 프록시를 경유하는 `@Cacheable`보다 테스트 의도를 더 명확하게 드러낸다
   (`PerformanceServiceTest`의 캐시 히트/미스 테스트 참고).
3. **TTL·무효화 정책이 아직 확정되지 않은 과도기** — 이슈 #56은 Cache-Aside 흐름만 구현하고
   TTL·무효화 전략은 후속 이슈에서 별도로 정하기로 했다(`PerformanceCacheRepository`의 TTL은
   플레이스홀더). 정책이 바뀔 걸 이미 알고 있는 상태에서는, `@CacheEvict` 등으로 선언을 흩어놓기보다
   Repository 한 곳에서 캐시 접근을 전부 통제하는 쪽이 다음 이슈에서 정책을 바꿀 때 변경 범위가 좁다.
4. **self-invocation 리스크 회피** — 다음 이슈(TTL·무효화)에서는 `updatePerformance`/
   `deletePerformance`가 같은 `PerformanceService` 안에서 캐시를 무효화해야 할 가능성이 높다.
   `@CacheEvict`를 같은 클래스의 다른 `@Transactional` 메서드에서 호출하면 프록시를 우회해
   무시되는 self-invocation 문제가 생기는데, Repository를 직접 호출하는 구조는 이 문제 자체가
   발생하지 않는다.

# 감수하는 단점과 한계

- **보일러플레이트** — `@Cacheable` 한 줄이면 될 일을 Repository 클래스 + JSON 직렬화/역직렬화
  코드로 직접 작성해야 한다. 캐싱 대상 조회가 늘어나면(예: 목록 조회도 캐싱하게 될 경우) 이
  직렬화 로직이 조회별로 반복될 수 있다 — 필요해지면 공통 JSON 캐시 Repository로 추출하는 것을
  검토할 것.
- **TTL 갱신·삭제 등 캐시 관리 기능을 직접 구현해야 함** — `@Cacheable`이 제공하는 `@CacheEvict`,
  캐시 이름 기반 일괄 무효화 같은 기능이 없어, 다음 이슈(TTL·무효화 전략)에서 필요한 무효화
  로직도 `PerformanceCacheRepository`에 메서드를 직접 추가하는 방식으로 확장해야 한다.
- 이 판단은 "이 프로젝트, 이 시점"의 조건(대기열에 이미 같은 패턴이 있고, 캐싱 대상이 아직
  상세 조회 하나뿐)을 전제로 한다. 캐싱 대상 API가 여러 도메인으로 늘어나 반복되는 캐시
  Repository가 많아지면, 그때는 `@Cacheable` + 커스텀 `KeyGenerator`로 전환하는 비용과
  반복 보일러플레이트를 유지하는 비용을 다시 비교해야 한다.

# 실측 테스트 결과

`@Cacheable` 방식과의 성능(응답 지연, 처리량) 비교 벤치마크는 아직 수행하지 않았다 — 테스트 예정.
현재는 Testcontainers 기반 통합 테스트(`PerformanceCacheRepositoryIT`)로 캐시 저장/조회가
의도대로 동작하는지만 기능적으로 검증한 상태다.

# 정리

이 프로젝트는 이미 `WaitingQueueRepository`로 "Repository가 `StringRedisTemplate`을 직접 감싼다"는
Redis 접근 컨벤션을 확립해 두었고, Cache-Aside 흐름 자체도 표준적이라 어느 방식으로 구현해도 동작은
같다. 선택을 가른 것은 성능 차이가 아니라 **기존 컨벤션과의 일관성**, **캐시 히트/미스를 테스트에서
직접 단정할 수 있는지**, 그리고 **아직 정책이 확정되지 않은 TTL·무효화를 다음 이슈에서 다룰 때
self-invocation 없이 확장할 수 있는지**였다. `@Cacheable`이 더 적합해지는 시점(캐싱 대상이 여러
도메인으로 늘어나 반복 보일러플레이트가 유지 비용을 넘어서는 시점)이 오면 그때 재검토한다.
