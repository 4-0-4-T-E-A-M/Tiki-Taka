## JWT role claim 적용 트레이드오프

### 1. 배경

기존 `JwtAuthenticationFilter`는 JWT에서 `userId`를 추출한 후 사용자의 권한을 확인하기 위해 매 요청마다 `UserRepository.findById()`를 호출하는 문제가 발생했다.

이 구조에서는 인증된 모든 API 요청마다 사용자 DB 조회가 추가된다.

특히 좌석 예매처럼 k6가 반복적으로 요청하는 경로에서도 동일한 조회가 발생하므로, 부하 테스트 결과에 좌석 락 경합뿐만 아니라 인증 필터의 DB 조회 비용이 함께 포함될 수 있는 가능성이 있다는 리뷰

Tiki-Taka 프로젝트의 핵심 목적은 대량 동시 트래픽 환경에서 예매, 락, 대기열 구조의 동작과 성능을 검증하는 것이다. 따라서 일반 인증 과정에서 발생하는 불필요한 DB 조회를 제거할 필요가 있다고 판단하였다, 해당 방법에 대하여 **`옵션 1 — access token claim에 role 포함` 을 선택하기로 하였다.**

### 2. 선택한 방식

Access Token에 `userId`와 `role`을 claim으로 포함하고, `JwtAuthenticationFilter`는 토큰의 claim만으로 인증 객체를 생성한다.

```
Access Token
- userId
- role
- tokenType: ACCESS
- issuedAt
- expiration
```

Refresh Token에는 role을 포함하지 않고 `userId`와 토큰 종류만 포함한다.

```
Refresh Token
- userId
- tokenType: REFRESH
- issuedAt
- expiration
```

일반 API 요청에서는 사용자 DB를 조회하지 않는다. Access Token 재발급 시점에는 DB에서 사용자를 조회하여 사용자 존재 여부와 최신 role을 확인한 뒤 새로운 Access Token을 발급한다.

### 3. 선택 이유

첫째, 일반 API 요청마다 발생하던 사용자 DB 조회를 제거할 수 있다.

기존 구조에서는 예매 요청 100건이 발생하면 인증 필터에서도 최대 100회의 사용자 조회가 추가로 발생하였다. Access Token의 role claim을 사용하면 일반 요청은 JWT 서명과 만료 시간, tokenType, role만 검증하여 인증을 완료할 수 있다.

이를 통해 부하 테스트에서 인증 DB 조회 비용이 좌석 락과 예매 트랜잭션 성능에 섞이는 것을 줄일 수 있다.

둘째, 서버 확장 시 사용자 세션 또는 사용자 DB 조회에 대한 의존도를 줄일 수 있다.

각 서버는 동일한 JWT 서명 키를 이용해 Access Token을 독립적으로 검증할 수 있다. 따라서 일반 인증을 위해 사용자별 세션 상태를 유지하거나 공용 저장소를 조회하지 않아도 된다.

셋째, 재발급 시점에는 최신 권한을 반영할 수 있다.

Refresh Token에 role을 포함하지 않고 재발급 시 DB에서 사용자의 최신 role을 조회한다. 이를 통해 이전 Refresh Token에 저장된 관리자 권한이 권한 강등 이후에도 계속 사용되는 문제를 방지한다.

### 4. 장점

- 일반 인증 요청에서 사용자 DB 조회가 제거된다.
- 예매 트래픽의 TPS와 p95 지표에 인증 DB 조회 비용이 섞이는 것을 줄일 수 있다.
- `JwtAuthenticationFilter`에서 `UserRepository` 의존성을 제거할 수 있다.
- 서버가 여러 대로 확장되어도 각 서버가 독립적으로 Access Token을 검증할 수 있다.
- Refresh Token 재발급 시 DB의 최신 role을 새로운 Access Token에 반영할 수 있다.
- 별도의 커스텀 `AuthorizationManager`를 구현하지 않아도 Spring Security의 권한 검사를 사용할 수 있다.

### 5. 단점

Access Token에 포함된 role은 토큰 발급 당시의 권한 정보이다. 따라서 DB에서 사용자의 role이 변경되더라도 기존 Access Token에는 즉시 반영되지 않는다.

예를 들어 ADMIN 권한을 가진 사용자가 USER로 강등되더라도 기존 Access Token이 만료되기 전까지는 ADMIN 권한이 유지될 수 있다.

또한 일반 요청에서 사용자 DB를 조회하지 않으므로, 토큰 발급 이후 사용자가 탈퇴하거나 삭제되어도 기존 Access Token의 만료 전까지 요청이 통과할 수 있다.

로그아웃 시 Refresh Token 쿠키를 제거하더라도 이미 발급된 Access Token은 만료 전까지 유효하다.

즉시 토큰 폐기가 필요하다면 Redis 블랙리스트, token version 또는 별도의 서버 상태 관리가 필요하다.

### 6. 위험 완화 방안

Access Token의 유효 시간을 기존 1시간보다 짧은 약 30분으로 설정한다. 이를 통해 권한 강등, 사용자 삭제 또는 로그아웃 이후 기존 Access Token이 유효한 시간을 제한한다.

재발급 시에는 반드시 DB에서 사용자를 조회하여 다음 항목을 확인한다.

- 사용자가 현재 존재하는지
- 사용 가능한 계정 상태인지
- 현재 role이 무엇인지

Access Token과 Refresh Token에는 `tokenType` claim을 포함하고, 일반 인증 필터에서는 `ACCESS` 타입의 토큰만 허용한다. 재발급 API에서는 `REFRESH` 타입의 토큰만 허용한다.

role claim이 누락되거나 지원하지 않는 값이면 기본 권한을 부여하지 않고 인증 실패로 처리한다.

### 7. 감수 가능한 범위

Tiki-Taka는 결제나 금융 서비스를 직접 운영하는 시스템이 아니라 대량 트래픽, 예매, 분산 락 및 대기열 동작을 실험하는 시뮬레이션 플랫폼이다.

현재 관리자 기능의 위험도가 높지 않고 권한 변경이 빈번하지 않다는 전제에서는, Access Token 만료 전까지 권한 변경이 지연되는 문제를 감수할 수 있다고 판단하였다.

대신 해당 지연 시간을 줄이기 위해 Access Token 만료 시간을 약 30분으로 제한하고, 재발급 시 최신 사용자 정보와 role을 조회한다.

### 8. 향후 변경 기준

다음 요구사항이 추가되면 현재 방식만으로는 부족하며 추가적인 서버 상태 관리가 필요하다.

- 관리자 권한 회수를 즉시 반영해야 하는 경우
- 사용자 정지 또는 탈퇴를 즉시 반영해야 하는 경우
- 로그아웃 시 기존 Access Token을 즉시 폐기해야 하는 경우
- 관리자 API가 결제, 환불 또는 개인정보 변경과 같은 민감한 작업을 수행하는 경우

이 경우 일반 API에서는 JWT claim을 계속 사용하되, 민감한 관리자 API에서만 DB 또는 Redis를 조회하는 방식이나 Redis 기반 Access Token 블랙리스트 및 token version 방식을 검토한다.

### 9. 최종 결정

일반 API 요청의 성능과 서버 확장성을 우선하여 Access Token에 role claim을 포함한다.

일반 요청에서는 사용자 DB를 조회하지 않고 JWT claim만으로 인증 객체를 생성한다.

Refresh Token에는 role을 포함하지 않으며, 재발급 시 DB에서 최신 사용자 role을 조회하여 새로운 Access Token에 반영한다.

이 방식은 일반 인증 경로의 DB 부하를 제거하면서도 권한 변경을 다음 재발급 시점에 반영할 수 있는 성능과 보안 사이의 절충안이다.