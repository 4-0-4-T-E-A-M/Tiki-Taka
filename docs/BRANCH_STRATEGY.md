# 브랜치 전략

## 브랜치 구조

```
main        ← 항상 배포 가능한 상태 (보호 브랜치, 직접 push 금지)
develop     ← 통합 브랜치 (기본 작업 브랜치, 여기서 매주 리뷰)
feature/*   ← 실제 작업 브랜치
```

- `main` : 스프린트(주차) 종료 시점에 develop에서 merge. 태그(v0.1, v0.2 ...)로 주차별 스냅샷 관리
- `develop` : 평소 작업은 전부 여기로 PR. 팀원 3명 모두 여기 기준으로 브랜치를 딴다
- `feature/*` : 이슈 하나당 브랜치 하나

## 브랜치 네이밍

```
feature/#이슈번호-도메인-설명
fix/#이슈번호-설명
refactor/#이슈번호-설명
docs/#이슈번호-설명        ← 트레이드오프 문서화 등
```

예시
```
feature/#12-booking-reservation-api
feature/#15-concert-seat-status
fix/#23-redis-lock-ttl-bug
docs/#8-jwt-vs-session-tradeoff
refactor/#31-n+1-fetch-join
```

도메인 접두어 기준 (역할 분담표 기준)
- `booking-` : 노주희 (예매)
- `concert-` / `seat-` / `queue-` : 신선우 (공연·좌석·대기열)
- `user-` / `notification-` : 조준형 (유저·알림)

## 작업 흐름

1. Issue 생성 (템플릿 사용) → 담당자 지정, 라벨 지정
2. `develop`에서 `feature/#이슈번호-설명` 브랜치 생성
3. 작업 → 커밋 → push (작업 중 `develop`이 앞서나갔다면 push/PR 갱신 전에 rebase, 아래 참고)
4. `develop`으로 PR 생성 (PR 템플릿 사용, 이슈 연결)
5. 팀원 중 1명 이상 리뷰 승인 후 merge (교차 리뷰 원칙 — 본인 도메인 PR은 다른 2명이 리뷰)
6. 주차 종료 시 `develop` → `main` merge + 태그

## 로컬 작업 중 rebase, PR 병합은 squash

- **로컬 feature 브랜치**: `develop`이 먼저 나갔으면 merge commit 대신 rebase로 맞춘다.
  ```
  git fetch origin
  git rebase origin/develop
  git push --force-with-lease   # 이미 push한 적 있는 브랜치라면
  ```
  merge commit이 안 생겨서 PR diff와 리뷰가 깔끔해진다.
- **PR 병합**: 브랜치 보호 규칙대로 Squash and merge를 사용한다. develop 히스토리에는 PR당
  커밋 1개만 남지만, 원래의 세부 커밋은 merge된 PR의 Commits 탭에 그대로 남아있으니
  필요하면 거기서 확인하면 된다.
- **주의**: rebase는 본인 소유의 `feature/*` 브랜치에서만 한다. 팀원과 공유하는
  `develop`/`main`은 절대 rebase하지 않는다.

## 커밋 메시지 컨벤션 (Conventional Commits)

```
feat: 예매 생성 API 구현
fix: 좌석 상태 동시성 이슈 수정
refactor: 서비스 레이어 책임 분리
test: 예매 동시성 통합 테스트 추가
docs: Redis 분산락 트레이드오프 문서화
chore: Kafka 브로커 Docker 세팅
```

- 제목은 50자 이내, 한글 사용 가능
- 본문 필요 시 `-` 로 상세 내역 나열

## 브랜치 보호 규칙 (GitHub Settings → Branches)

- `main`, `develop` : 직접 push 금지, PR 필수
- `develop` : 최소 1명 승인 필요
- `main` : 최소 2명 승인 필요 (팀 전체 검토)
- merge 방식: Squash and merge 권장 (커밋 히스토리 깔끔하게)
