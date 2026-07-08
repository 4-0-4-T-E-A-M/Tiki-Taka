---
description: 로드맵(issues.yaml) 기준 다음 주차 GitHub 이슈/마일스톤을 Claude가 직접 gh CLI로 생성
---

사용자가 "이번 주차 이슈 만들어줘" 또는 유사한 요청을 하면, 별도 스크립트 없이
아래 절차를 직접 bash로 수행한다. (필요한 건 `gh` CLI 뿐이며 python/yaml 라이브러리 불필요)

## 0. 사전 확인
- `gh auth status` 로 인증 상태 확인. 인증 안 되어 있으면 `gh auth login` 안내 후 중단.
- `automation/issues.yaml` 존재 확인. 없으면 중단하고 알림.

## 1. 대상 주차 결정
- `automation/issues.yaml` 을 읽는다 (view 도구 사용, 별도 파서 불필요 — 파일 자체가
  사람이 읽기 쉬운 구조이므로 그대로 해석 가능).
- `gh api repos/{repo}/milestones --paginate` 로 이미 생성된 milestone 목록을 가져온다.
- yaml의 `weeks` 순서대로 훑어서, milestone 제목이 아직 GitHub에 없는 **가장 빠른 주차**를
  이번에 생성할 대상으로 정한다. (사용자가 특정 주차를 명시하면 그 주차를 그대로 사용)

## 2. Dry-run 요약
- 실제 생성 전에, 이번에 만들 milestone 제목 / 마감일 / 이슈 목록(제목, 담당자, 라벨)을
  사용자에게 정리해서 보여주고 진행 여부를 확인받는다.

## 2.5. 이슈 제목 형식
- 이슈 제목에는 도메인·담당자 정보를 넣지 않는다. 그 정보는 전적으로 `labels`(도메인 라벨)와
  `assignee` 필드가 담당한다 — 제목은 `[Type] 설명` 형식만 쓴다.
  - 예: `[Feat] 예매(Booking) 엔티티 구현`, `[Docs] ★ 분산 락 선택 이유 트레이드오프 문서화`
- `Type`은 Title Case 영문 단어이며, 아래 6종 중 하나만 쓴다: `Feat`, `Test`, `Perf`,
  `Refactor`, `Docs`, `Chore`. 새 타입 태그를 임의로 만들지 않는다.
- `Type`은 그 이슈의 타입 라벨(`docs/LABEL_SYSTEM.md`의 10종)로부터 아래 우선순위로 기계적으로
  정해진다 (여러 타입 라벨이 같이 붙는 경우가 있으므로 순서대로 첫 매치를 사용):
  1. `test` → `Test`
  2. `perf` → `Perf`
  3. `refactor` → `Refactor`
  4. `feature` → `Feat`
  5. `docs` 또는 `trade-off-doc` → `Docs`
  6. 그 외 (`infra`, `review`, `team`, `checkpoint`만 있거나 위 5개에 해당하는 라벨이 하나도
     없는 경우) → `Chore`
- 즉 라벨이 먼저 정해지고 제목의 `[Type]`은 그 라벨에서 파생된다 — 제목을 보고 라벨을
  따로 고민하지 않는다.

## 3. Milestone 생성 (없을 때만)
```
gh api repos/{repo}/milestones -f title="{milestone_title}" -f due_on="{due_on}T00:00:00Z" -f state=open
```
- due_on 은 config.start_date(1주차 milestone 마감일, 화요일) 기준으로 계산:
  due_on = start_date + (week-1)*7일 (매주 화요일 마감)

## 4. 라벨 생성 (없을 때만, 실패해도 무시)
해당 주차 이슈들에 쓰인 라벨마다:
```
gh label create "{label}" --repo {repo} --force
```
`--color`는 지정하지 않는다 — 생략하면 `gh`가 알아서 랜덤 색을 배정한다. 색상을 임의로 고르지 않는다.
라벨 부착 규칙(도메인 라벨 + 타입 라벨 조합)은 `docs/LABEL_SYSTEM.md`를 따른다.
issues.yaml에 없는 새 라벨을 임의로 만들지 않는다.

## 5. 이슈 body 작성 (issue template 기반, 제목별 실제 내용 채우기)
- 모든 이슈의 body는 `.github/ISSUE_TEMPLATE/04-weekly-task.md`의 구조(요약 / Why / What / How /
  Checklist (Definition of Done) / Notes)를 기반으로 작성한다. "자동 생성됨" 같은 의미 없는
  문구를 넣지 않는다. 생성할 이슈마다 매번 그 제목에 맞게 새로 작성하며, 정해진 템플릿
  문자열을 그대로 복붙하지 않는다.
- 작성 시 아래 소스를 근거로 삼는다 (존재하는 것만 참고, 없으면 건너뜀):
  - `CLAUDE.md` — 기술 스택, 패키지 구조 등 프로젝트 전반 정보
  - `docs/ROADMAP.md` — 담당자별 역할, 전체 예매 흐름, 주차별 맥락(해당 이슈가 그 흐름의
    어디에 해당하는지)
  - `docs/LABEL_SYSTEM.md` — 라벨이 가리키는 작업 성격
  - 이슈 라벨의 도메인(`booking`/`performance-seat`/`user-notification`)에 해당하는
    `src/main/java` 하위 패키지를 가볍게 훑어서, 이미 구현된 부분과 겹치지 않게 하고
    현재 코드 상태에 맞는 설명을 쓴다 (코드가 아직 없으면 이 단계는 생략).
- 6개 섹션은 전부 필수로 채운다 (Notes만 내용이 없으면 "특이사항 없음" 정도로 짧게 남기거나 생략 가능):
  ```
  ## 요약
  (이 이슈가 무엇인지 1~2문장)

  ## Why
  (왜 필요한지 — 로드맵/전체 예매 흐름에서 이 작업의 위치)

  ## What
  (무엇을 구현/작성해야 하는지, 구체적으로)

  ## How
  (아래 "How 작성 원칙" 참고 — 설계 여지가 있는 이슈는 정답이 아니라 고민할 질문/관점을 적는다)

  ## Checklist (Definition of Done)
  - [ ] (이 이슈가 끝났다고 볼 수 있는 기준을 체크리스트 항목으로)

  ## Notes (optional)
  (참고 링크, 관련 이슈, 추가로 알아두면 좋은 것 — 없으면 생략)
  ```

### How 작성 원칙 (중요)

이 프로젝트의 목적은 백엔드 설계 역량을 기르고 보여주는 연습이다. How에 추천이나 힌트를
적는 것은 괜찮지만, 추천 하나만 던지고 끝내면 담당자가 고민 없이 그대로 따르게 된다.
그래서 이슈를 두 종류로 나눠서 How를 다르게 쓴다.

- **설계 판단이 필요한 이슈** (분산락 방식, 캐시 전략, 인덱스/쿼리 설계, 인증 방식,
  메시징/파티셔닝 설계, 레이어 책임 분리, 그리고 특히 `trade-off-doc` 라벨이 붙은 모든
  이슈): 흔히 쓰이는 방식이나 추천을 적어도 되지만, 반드시 그와 함께 고려해볼 다른
  방식·관점도 나란히 제시한다.
  - 예: "Redisson 분산락이 일반적인 선택지지만, 락 경합이 심하지 않다면 DB 비관적 락
    (`SELECT ... FOR UPDATE`)만으로도 충분할 수 있다 — 이 프로젝트의 동시 요청 규모에서
    어느 쪽이 더 적합할지, TTL을 짧게/길게 가져갈 때 각각 무엇을 잃고 얻는지 비교해볼 것"
  - 왜 그 추천이 흔히 쓰이는지 정도는 설명해도 되지만, "이게 정답이니 이대로 구현하라"는
    식으로 결론까지 대신 내려주지는 않는다 — 대안과 비교 관점을 반드시 함께 남긴다.
  - 최종 선택과 그 이유는 담당자 본인이 이슈나 트레이드오프 문서에 채우게 한다.
- **설계 여지가 거의 없는 기계적 작업** (레포 생성, 템플릿 작성, 환경 세팅, 로컬 실행
  확인 같은 체크포인트 등): 이런 이슈는 고민할 지점이 없으므로 지금처럼 구체적인 절차를
  안내해도 된다.
- 헷갈리면 "이 작업에 정답이 하나뿐인가, 아니면 트레이드오프가 있는 결정인가"로 판단한다.
  트레이드오프가 있다면 추천은 주되 반드시 대안 비교 관점을 함께 적는다.

- `trade-off-doc` 라벨이 붙은 이슈는 Why에 "무엇을, 왜 비교하고 선택해야 하는지"를
  명확히 적어 문서화 목적이 드러나게 하고, How는 위 원칙대로 추천 + 대안 비교 관점을 함께 쓴다.
- `team`/`checkpoint` 라벨만 있고 도메인 라벨이 없는 공통 이슈는 각 섹션을 간단히,
  확인/조율 목적에 맞게 짧게 쓴다.

## 6. 이슈 생성 (중복 체크 후)
- 먼저 `gh issue list --repo {repo} --state all --limit 500 --json title,assignees` 로 기존
  이슈의 (제목, 담당자) 목록을 가져온다.
- 중복 체크는 **제목 단독이 아니라 (title, assignee) 조합**으로 한다. 제목에서 도메인·담당자
  표기를 뺐기 때문에, 담당자만 다르고 설명은 동일한 개인 이슈(예: 담당자별 "[Docs] 개인 면접
  Q&A 자기 정리")가 존재할 수 있다 — 이런 케이스는 서로 다른 이슈이므로 제목이 같다는
  이유로 스킵하면 안 된다.
  - 개인 이슈: (title, 해당 담당자 1명) 조합이 기존 목록에 이미 있으면 스킵.
  - `assignee` 필드가 없는 공통 이슈: (title, 팀원 전체 assignee 집합) 조합이 기존 목록의
    assignees 집합과 동일하면 스킵.
- yaml의 해당 주차 이슈들을 순회하며, (title, assignee) 조합이 기존 목록에 없는 것만 5단계에서
  작성한 body로 아래처럼 생성:
```
gh issue create --repo {repo} \
  --title "{title}" \
  --body "{5단계에서 작성한 body}" \
  --milestone "{milestone_title}" \
  --label "{labels_comma_separated}" \
  --assignee "{github_id}"
```
- `assignee` 필드가 있는 개인 이슈는 그 담당자 1명만 `--assignee`로 지정한다.
- `assignee` 필드가 없는 공통 이슈는 팀원 전체가 관련된 일이므로, `assignees` 매핑에 있는
  팀원 전원을 `--assignee`로 각각 지정한다 (예: `--assignee "JuheeNoh123" --assignee "sunwoo1256" --assignee "junhyung001"`).
  담당자를 비워두지 않는다.
- 이미 존재하는 (title, assignee) 조합은 건너뛰고 사용자에게 스킵된 항목으로 보고한다.

## 7. 결과 보고
생성된 이슈 수, 스킵된 이슈 수, milestone 링크(gh api 응답의 html_url 활용)를
간단히 요약해서 보고한다. 실패한 항목이 있으면 원인을 함께 설명한다.

## 주의사항
- 이 절차는 매번 실행 시 GitHub 상태(milestone/issue 목록)를 다시 조회해서 판단하므로,
  이전 실행 여부를 별도로 기록해두지 않는다 — GitHub 자체가 진실의 원천(source of truth).
- 특정 주차를 다시 실행해야 하는 경우, 사용자가 명시적으로 "3주차 다시 만들어줘"처럼
  말하면 해당 주차 번호를 그대로 사용한다.
- 대량의 gh 명령을 연달아 실행하기 전에는 항상 2단계(dry-run 요약)에서 사용자 확인을 받는다.