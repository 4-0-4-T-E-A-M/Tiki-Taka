# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the app (requires .env, see below)
./gradlew bootRun

# Build
./gradlew build

# Run all tests
./gradlew test

# Run a single test class / method
./gradlew test --tests "io.github.team404.tikitaka.TikitakaApplicationTests"
./gradlew test --tests "*.TikitakaApplicationTests.contextLoads"
```

Environment: copy `.env.example` to `.env` and fill in `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (PostgreSQL) and `REDIS_HOST` / `REDIS_PORT` (Redis). `.env` is loaded automatically via `spring-dotenv` (`me.paulschwarz:springboot3-dotenv`) — no need to export env vars manually. `docker compose up -d` starts local PostgreSQL and Redis containers (see `docker-compose.yml`).

## Project

Tiki-Taka is a concert-ticketing **practice/simulation platform**, not a real ticketing service. Its point is to let a user re-experience the exact high-traffic conditions of a real ticket-opening moment (mass concurrent seat selection) and practice booking quickly, while the team demonstrates backend skills around concurrency control, payment-flow stability, and search performance.

Core practice-session flow (see `docs/ROADMAP.md` for the always-on booking flow between domains):
1. User picks a concert/session and starts a "practice session" at a scheduled open time.
2. At open time, k6 fires many simulated concurrent bot requests at the same seats the real user is trying to book.
3. Seat selection goes through the Redis distributed lock / queue path exactly like a real request — the user is competing against the bots for the same lock.
4. After the session, the user gets a personal result (did they get the seat, how long it took, queue rank) plus session-wide metrics (TPS, lock contention count, p95 latency) — the latter fed by Prometheus/Grafana and k6 results stored in InfluxDB.

Seat state machine: `AVAILABLE → RESERVED (temp hold, 5 min TTL) → CONFIRMED/PAID → CANCELLED`. A seat lock that isn't paid within its TTL must release back to `AVAILABLE` automatically.

## Architecture

Single-module Spring Boot app (Java 21, Gradle), root package `io.github.team404.tikitaka`. Currently just the JPA/Web starter skeleton — most of the stack below is the target architecture from `docs/ROADMAP.md`'s 12-week plan, not yet implemented. When picking up a roadmap issue, check what already exists under `src/main/java` before assuming a piece is greenfield.

| Layer | Tech | Role |
|---|---|---|
| API | Spring Boot (Web, JPA) | REST endpoints, business logic |
| DB | PostgreSQL | Chosen specifically for `SELECT ... FOR UPDATE` / row locking to guarantee only one buyer wins a contested seat, and for transactional all-or-nothing seat+order writes |
| Lock/Cache | Redis (Redisson) | Distributed lock for seat selection, popular-concert caching (Cache-Aside), waiting-queue (Sorted Set) |
| Queue/Events | Kafka | Booking-confirmed event → email notification consumer + booking-stats consumer (decoupled, multi-consumer) |
| Search | Elasticsearch (nori analyzer) | Concert search/autocomplete; PostgreSQL is the fallback if ES is down |
| Observability | Prometheus + Grafana, Jaeger, Loki | Metrics/tracing/logs, including practice-session TPS and lock-contention dashboards |
| Load testing | k6 → InfluxDB | Drives the bot traffic in a practice session and stores results for the post-session report |

Domain ownership (also drives branch prefixes and issue labels — see below): booking (예매) / concert-seat (공연·좌석·대기열, labeled `performance-seat`) / user-notification (유저·알림). Full per-week task breakdown and the end-to-end booking sequence diagram live in `docs/ROADMAP.md`.

## Repo conventions

- **Labels**: exactly the 13 defined in `docs/LABEL_SYSTEM.md` (10 type labels + 3 domain labels: `booking` / `performance-seat` / `user-notification`). Don't invent new ones — personal issues get a domain label + a type label; team/checkpoint issues skip the domain label.
- **Branches**: `feature|fix|refactor|docs/#이슈번호-설명`, always cut from `develop`, PR back into `develop` (see `docs/BRANCH_STRATEGY.md`). `main` only receives weekly squash-merges from `develop` with a version tag.
- **Commits**: Conventional Commits (`feat|fix|refactor|test|docs|chore: ...`), Korean summary OK, ≤50 char subject.
- **Reviews**: cross-review only — a domain's PRs must be reviewed by one of the *other* two members, not the domain owner.
- **Issue/PR templates**: `.github/ISSUE_TEMPLATE/` (feature / bug / `★` trade-off-doc / weekly-task) and `.github/PULL_REQUEST_TEMPLATE.md` already encode the required sections (spec, checklist, trade-off doc link) — reuse them rather than freeform issue bodies. `04-weekly-task.md` (요약/Why/What/How/Checklist/Notes) is the base structure for roadmap-driven issues.
- **Automated weekly issue creation**: `.claude/commands/weekly-issues.md` reads `automation/issues.yaml` and uses `gh` directly (milestone → labels → issue, with per-issue bodies drafted per the `04-weekly-task.md` template using this file + `docs/ROADMAP.md` + the relevant domain code) — see that file before changing the issue-generation flow.
