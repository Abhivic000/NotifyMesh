# Event-Driven Notification Platform

A reusable notification infrastructure: external services publish business events (e.g.
`ORDER_CREATED`, `PAYMENT_FAILED`), and this platform reliably delivers notifications through
email/SMS/push via Kafka, a transactional outbox, idempotent consumers, retries, and a
dead-letter queue.

Portfolio project — full requirements in
[`event_driven_notification_platform_project_requirement_document.txt`](./event_driven_notification_platform_project_requirement_document.txt).

## Status

Actively being built. See `docs/adr/` for architecture decisions as they're made.

## Prerequisites

- Java 21 (`JAVA_HOME` set)
- Docker Desktop
- No global Maven needed — use the wrapper (`./mvnw` / `mvnw.cmd`)

## Running locally

```bash
# 1. Copy env template and adjust if needed (defaults work for local dev)
cp .env.example .env

# 2. Start infrastructure (Postgres, Redis, Kafka, Kafka UI)
docker compose up -d
docker compose ps   # all 4 should show "healthy"

# 3. Build everything
./mvnw.cmd package

# 4. Run a service, e.g.
./mvnw.cmd -pl services/event-ingestion-service spring-boot:run
```

Kafka UI: http://localhost:8080

## Project layout

- `services/` — deployable Spring Boot apps (api-gateway, event-ingestion-service, notification-service, email/sms/push-worker)
- `libs/` — shared, non-deployable code (event contract DTOs, observability helpers)
- `infrastructure/` — Kafka/Prometheus/Grafana/OTel config
- `docs/` — architecture notes, ADRs, runbooks
- `tests/` — cross-service e2e and k6 load tests
