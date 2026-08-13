# 1. 유저가 실제로 보는 서비스 흐름

## 최종 유저 Flow

```
1. 로그인한다.

2. 공연 목록을 본다.

3. 공연 상세 페이지에 들어간다.

4. 공연 회차를 선택한다.
   예: 2026-07-20 19:00 공연

5. 구역을 선택한다.
   예: VIP석 / R석 / A구역 / B구역

6. 예매할 좌석 수량을 선택한다.
   예: 1매, 2매

7. 시뮬레이션 실행 시간을 선택한다.
   예: 10시 10분

8. 경쟁 레벨을 선택한다.
   예: 보통 / 치열 / 매우 치열

9. 시뮬레이션을 예약한다.

10. 예약한 시간이 되면 서버가 자동으로 경쟁 시뮬레이션을 실행한다.

11. 사용자는 결과를 확인한다.
    - 성공
    - 실패

12. 성공했다면 결제를 진행한다.

13. 결제 성공 시 예매가 확정된다.

14. 사용자는 내 예매 내역에서 확정된 좌석을 확인한다.
```

이게 우리가 가져갈 **진짜 유저 관점 Flow**야.

---

# 2. 유저가 직접 선택하는 것

유저가 직접 선택하는 값은 딱 이거야.

| 유저 선택 항목 | 예시 | 저장 위치 |
| --- | --- | --- |
| 공연 | 아이유 콘서트 | `performances` |
| 회차 | 7월 20일 19:00 | `performance_schedules` |
| 구역 | VIP / R / A구역 | `sections` |
| 수량 | 1매 / 2매 | `simulations` 또는 `reservations` |
| 실행 시간 | 10:10 | `simulations.scheduled_start_time` |
| 경쟁 레벨 | 보통 / 치열 / 매우 치열 | `simulations.competition_level` 추천 |

여기서 중요한 건 **유저는 좌석을 직접 고르는 게 아니라, 구역과 수량을 선택하는 방식**으로 가는 게 좋다는 거야.

왜냐하면 이 프로젝트의 핵심은 “좌석 선택 UI”가 아니라 **고트래픽 환경에서 좌석 선점 경쟁을 시뮬레이션하는 것**이기 때문.

---

# 3. 유저가 좌석을 직접 고르지 않는 이유

이 프로젝트에서는 유저가 이렇게 하는 게 자연스러움.

```
VIP 구역에서 2매 예매 시도
```

그러면 서버와 k6가 실제 시뮬레이션 중에 해당 구역 좌석 중에서 가능한 좌석을 잡으려고 함.

즉, 유저 입장에서는:

```
“내가 VIP 구역 2장을 잡을 수 있을까?”
```

를 테스트하는 서비스가 되는 거야.

반대로 유저가 특정 좌석을 직접 고르면:

```
A열 3번 좌석을 잡을 수 있을까?
```

가 되는데, 이러면 티켓팅 경쟁보다 **단일 좌석 락 테스트**에 가까워짐.

그래서 최종 방향은 이게 좋음.

> 유저는 **구역 + 수량 + 실행 시간 + 경쟁 레벨**을 선택한다.
>
>
> 실제 좌석 배정은 시뮬레이션 실행 중 서버가 처리한다.
>

---

# 4. DB 관점에서 유저 Flow 정리

## 1단계. 유저 로그인

사용자 정보는 `users`에 있음.

```
users
- user_id
- email
- password
- name
- created_at
```

유저 입장에서는 로그인 후 모든 요청에 본인 정보가 붙음.

---

## 2단계. 공연 조회

```
performances
    ↓
performance_schedules
    ↓
sections
    ↓
seats
```

유저 화면은 이렇게 구성됨.

```
공연 목록
→ 공연 상세
→ 회차 선택
→ 구역 선택
→ 좌석 잔여 현황 확인
```

이때 유저가 볼 정보는:

```
공연명
공연 설명
포스터
회차 일시
구역명
전체 좌석 수
잔여 좌석 수
가격
좌석 등급
```

---

## 3단계. 시뮬레이션 생성

유저가 입력하는 값:

```json
{
  "scheduleId": 1,
  "sectionId": 3,
  "selectedQuantity": 2,
  "scheduledStartTime": "2026-07-10T10:10:00",
  "competitionLevel": "HIGH"
}
```

이 요청으로 `simulations` row가 생성됨.

이때 `simulations`는 이렇게 해석하면 됨.

> 사용자가 예약한 티켓팅 경쟁 체험 요청
>

즉, 아직 예매가 된 게 아님.

상태는:

```
simulation.status = SCHEDULED
```

---

# 5. 시뮬레이션 실행 Flow

예약 시간이 되면 서버가 실행함.

```
simulation.status = SCHEDULED
        ↓
simulation.status = RUNNING
        ↓
k6 실행
        ↓
가상 유저들이 해당 구역 좌석에 예매 요청
```

이때 중요한 건:

> 실제 유저 1명 + 가상 유저 N명이 같은 구역의 좌석을 두고 경쟁하는 구조
>

예를 들어:

```
유저 선택:
- 공연: 아이유 콘서트
- 회차: 7월 20일 19:00
- 구역: VIP
- 수량: 2매
- 경쟁 레벨: HIGH

서버 변환:
- HIGH → 가상 유저 500명
```

그러면 시뮬레이션 실행 시:

```
500명의 가상 유저가 VIP 구역 좌석을 랜덤하게 잡으려고 요청
+
실제 사용자의 요청도 함께 처리
```

---

# 6. 예매 성공 시 Flow

유저가 경쟁에서 성공하면 좌석이 먼저 `HELD` 상태가 됨.

```
seats.status = AVAILABLE
        ↓
seats.status = HELD
```

이때 `reservation`이 생성됨.

```
reservations.status = PENDING_PAYMENT
```

그리고 결제 요청이 생성됨.

```
payments.status = REQUESTED
```

즉 성공 직후 상태는 이거야.

| 테이블 | 상태 |
| --- | --- |
| `seats` | `HELD` |
| `reservations` | `PENDING_PAYMENT` |
| `payments` | `REQUESTED` |
| `reservation_seats` | 아직 생성 안 함 |

여기서 핵심은 이거야.

> `reservation_seats`는 아직 만들지 않는다.
>
>
> 왜냐하면 아직 결제 성공 전이기 때문이다.
>

---

# 7. 결제 성공 시 Flow

결제 성공하면 그때 최종 확정 처리함.

```
payments.status = SUCCESS
reservations.status = CONFIRMED
seats.status = RESERVED
reservation_seats row 생성
```

즉 이 순간에만 `reservation_seats`에 저장함.

```
reservation_seats
- reservation_id
- seat_id
- confirmed_at
- created_at
```

이게 네가 말한 방향이랑 정확히 맞음.

> `reservation_seats`는 확정된 좌석 테이블이다.
>

---

# 8. 결제 실패 / 시간초과 Flow

결제 실패 또는 시간초과면 예매는 확정되지 않음.

```
payments.status = FAILED 또는 TIMEOUT
reservations.status = FAILED 또는 EXPIRED
seats.status = AVAILABLE
reservation_seats 생성 안 함
```

즉 흐름은 이거야.

```
HELD 상태였던 좌석을 다시 AVAILABLE로 돌린다.
```

그래서 유저 입장에서는:

```
결제 시간이 초과되어 예매가 취소되었습니다.
좌석은 다시 선택 가능한 상태로 변경되었습니다.
```

라고 보여줄 수 있음.

---

# 9. 예매 실패 시 Flow

경쟁에서 실패하면 `reservation` 자체가 안 만들어질 수도 있음.

왜냐하면 좌석을 잡지 못했기 때문.

이 경우에는 `simulation_attempt_logs`에 실패 기록만 남김.

```
result = FAIL
failure_code = SEAT_HELD / SEAT_RESERVED / SOLD_OUT / LOCK_TIMEOUT
```

유저 화면에서는 이렇게 보여줌.

```
예매 실패

실패 사유:
다른 사용자가 먼저 좌석을 선점했습니다.

요청 도착 시간:
10:10:00.123

좌석 선점 시간:
10:10:00.101

차이:
22ms
```

즉, 유저에게는 기술 로그 그대로 보여주지 않고 **결과 리포트 형태**로 보여줘야 함.

---

# 10. 유저 화면 기준 페이지 구성

## 1. 로그인 / 회원가입

```
/login
/signup
```

유저는 로그인 후 서비스 이용 가능.

---

## 2. 공연 목록 페이지

```
/performances
```

보여줄 것:

```
공연 제목
포스터
공연 설명
```

---

## 3. 공연 상세 페이지

```
/performances/{performanceId}
```

보여줄 것:

```
공연 정보
회차 목록
```

---

## 4. 회차 선택 페이지

```
/performances/{performanceId}/schedules
```

보여줄 것:

```
7월 20일 19:00
7월 21일 19:00
7월 22일 18:00
```

---

## 5. 구역 선택 페이지

```
/schedules/{scheduleId}/sections
```

보여줄 것:

```
VIP 구역 - 잔여 40석 / 전체 100석
R 구역 - 잔여 120석 / 전체 300석
S 구역 - 잔여 200석 / 전체 500석
```

---

## 6. 시뮬레이션 생성 페이지

```
/simulations/new
```

유저 선택값:

```
회차
구역
수량
실행 시간
경쟁 레벨
```

생성 후:

```
시뮬레이션이 예약되었습니다.
10시 10분에 실행됩니다.
```

---

## 7. 내 시뮬레이션 목록

```
/my/simulations
```

보여줄 것:

```
공연명
회차
구역
수량
실행 시간
상태
결과
```

예시:

| 공연 | 회차 | 구역 | 수량 | 상태 | 결과 |
| --- | --- | --- | --- | --- | --- |
| 아이유 콘서트 | 7/20 19:00 | VIP | 2매 | 완료 | 성공 |
| 뉴진스 콘서트 | 7/21 18:00 | R | 1매 | 완료 | 실패 |
| 데이식스 콘서트 | 7/22 20:00 | S | 2매 | 예약됨 | 대기 중 |

---

## 8. 시뮬레이션 결과 상세

```
/simulations/{simulationId}/result
```

성공 시:

```
예매 성공

선점 좌석:
VIP A열 12번
VIP A열 13번

결제 제한 시간:
5분

[결제하기]
```

실패 시:

```
예매 실패

실패 사유:
다른 사용자가 먼저 좌석을 선점했습니다.

요청 도착 시간:
10:10:00.123

좌석 선점 시간:
10:10:00.101

차이:
22ms
```

---

## 9. 결제 페이지

```
/reservations/{reservationId}/payment
```

결제 성공 시:

```
결제가 완료되었습니다.
예매가 확정되었습니다.
```

이때 `reservation_seats` 생성.

---

## 10. 내 예매 내역

```
/my/reservations
```

여기에는 **결제 성공한 예매만 확정 좌석으로 보여주는 게 좋음.**

보여줄 것:

```
공연명
회차
구역
좌석
결제 금액
예매 상태
```

예시:

| 공연 | 회차 | 좌석 | 상태 |
| --- | --- | --- | --- |
| 아이유 콘서트 | 7/20 19:00 | VIP A열 12번, A열 13번 | 예매 확정 |

---

# 11. 최종 DB 상태 흐름

## 시뮬레이션 예약

```
simulations.status = SCHEDULED
```

## 시뮬레이션 실행 중

```
simulations.status = RUNNING
```

## 좌석 선점 성공

```
seats.status = HELD
reservations.status = PENDING_PAYMENT
payments.status = REQUESTED
```

## 결제 성공

```
payments.status = SUCCESS
reservations.status = CONFIRMED
seats.status = RESERVED
reservation_seats 생성
```

## 결제 실패

```
payments.status = FAILED
reservations.status = FAILED
seats.status = AVAILABLE
reservation_seats 생성 안 함
```

## 결제 시간초과

```
payments.status = TIMEOUT
reservations.status = EXPIRED
seats.status = AVAILABLE
reservation_seats 생성 안 함
```

## 경쟁 실패

```
simulation_attempt_logs.result = FAIL
reservation 생성 안 될 수 있음
payment 생성 안 됨
reservation_seats 생성 안 됨
```

---

# 12. 최종적으로 가져갈 원칙

## 원칙 1. 유저는 구역과 수량을 선택한다

```
유저가 직접 좌석을 고르는 서비스가 아니다.
유저는 특정 구역에서 몇 장을 잡을지 선택한다.
```

---

## 원칙 2. 시뮬레이션은 예매가 아니다

```
simulation = 티켓팅 경쟁 체험 요청
reservation = 좌석 선점에 성공한 예매 건
reservation_seats = 결제 성공 후 확정된 좌석
```

이 구분이 진짜 중요함.

---

## 원칙 3. 결제 전 좌석은 확정이 아니다

```
HELD = 결제 중
RESERVED = 결제 완료
```

그래서 결제 전에는 `reservation_seats`에 넣지 않는다.

---

## 원칙 4. 실패 로그는 유저 친화적으로 가공한다

DB에는 기술적으로 저장하되, 유저에게는 이렇게 보여준다.

```
22ms 차이로 실패했습니다.
다른 사용자가 먼저 좌석을 선점했습니다.
```

---

# 13. 최종 결론

이 프로젝트의 유저 관점 방향은 이렇게 가면 된다.

```
사용자는 공연을 고른다.
사용자는 회차를 고른다.
사용자는 구역을 고른다.
사용자는 수량을 고른다.
사용자는 실행 시간과 경쟁 레벨을 고른다.

서버는 해당 조건으로 시뮬레이션을 예약한다.
예약 시간이 되면 k6 기반 경쟁을 실행한다.

좌석 선점에 성공하면 seats.status = HELD가 된다.
이때 reservation은 PENDING_PAYMENT가 된다.

결제 성공 전까지는 확정 좌석이 아니다.
따라서 reservation_seats에는 저장하지 않는다.

결제 성공 시 seats.status = RESERVED가 되고,
reservations.status = CONFIRMED가 되며,
그때 reservation_seats에 확정 좌석을 저장한다.

실패한 경우에는 reservation_seats에 아무것도 저장하지 않고,
simulation_attempt_logs를 기반으로 실패 이유를 보여준다.
```