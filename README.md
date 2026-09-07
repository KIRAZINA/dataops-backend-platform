# DataOps Backend Platform

![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)
![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)
![Docker Ready](https://img.shields.io/badge/Docker-ready-blue.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

Production-ready multi-module Spring Boot monolith for data ingestion, storage, analytics, export, and operational monitoring.

## What It Does

- Ingests JSON, CSV, XML, and uploaded files
- Stores data in memory and persists it to H2
- Exposes paginated record browsing and analytics endpoints
- Exports data as JSON and CSV
- Ships with Swagger/OpenAPI, actuator health, Prometheus metrics, and Docker support
- Includes Apache Kafka integration for event streaming (disabled by default)
- Provides AOP-based logging for operational monitoring

## Stack

- Java 17+
- Spring Boot 3.3.4
- Spring Web + Spring Data JPA
- H2 + Flyway
- Jackson / Jackson XML
- springdoc OpenAPI
- Micrometer + Actuator + Prometheus
- Docker
- Apache Kafka
- Aspect-Oriented Programming (AOP)

## Quick Start

### Local

A non-empty API key is **required** at startup (the app refuses to boot without one outside of `dev`/`local`/`test` profiles). The key must be supplied via the `API_KEY` environment variable. In a fresh clone this is the most common reason the app fails to start.

Windows:

```powershell
$env:API_KEY = "your-secret-key"; .\run.bat
```

Linux / macOS / Git Bash:

```bash
export API_KEY="your-secret-key"
./run.sh
```

Maven Wrapper:

```bash
export API_KEY="your-secret-key"   # Windows PowerShell: $env:API_KEY = "your-secret-key"
./mvnw -pl dataops-platform-monolith spring-boot:run
```

Windows Maven Wrapper:

```powershell
$env:API_KEY = "your-secret-key"
.\mvnw.cmd -pl dataops-platform-monolith spring-boot:run
```

Once the app is up, authenticated calls must send the same key:

```bash
curl -H "X-API-Key: your-secret-key" http://localhost:8080/api/v1/ingest/json -d '{"value":42}' -H 'Content-Type: application/json'
```

For local-only experimentation you can start the app with the `dev` profile and skip the key:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw -pl dataops-platform-monolith spring-boot:run
```

### Docker

`docker-compose` will fail fast if `API_KEY` is not set in the shell environment — it is the only required variable for the app container:

```bash
export API_KEY="your-secret-key"
docker compose up --build
```

Or with plain Docker:

```bash
docker build -t dataops-platform .
docker run --rm -p 8080:8080 -e API_KEY="your-secret-key" dataops-platform
```

## Build

Run tests:

```bash
./mvnw test
```

Create the packaged jar:

```bash
./mvnw clean package
```

Artifact:

```text
dataops-platform-monolith/target/dataops-platform-monolith-0.0.1-SNAPSHOT.jar
```

## Main Endpoints

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:8080/actuator/prometheus`

API highlights:

- `POST /api/v1/ingest/json`
- `POST /api/v1/ingest/csv`
- `POST /api/v1/ingest/xml`
- `POST /api/v1/ingest/file`
- `GET /api/v1/records`
- `GET /api/v1/analytics/stats`
- `GET /api/v1/analytics/sorted`
- `GET /api/v1/storage/export/json`
- `GET /api/v1/storage/export/csv`

## Project Layout

```text
dataops-backend-platform
├── module-00-common-models
├── module-01-core
├── module-02-in-memory-engine
├── module-03-persistence
├── module-04-file-storage
├── module-05-analytics
├── module-06-streaming-kafka
├── module-07-api
├── module-08-aop-logging
├── common-test
└── dataops-platform-monolith
```

## Records Query Contract (`GET /api/v1/records`)

Paginated, filterable browse of the persisted record set. Backed by
`JpaRecordRepository.findAll(Specification, Pageable)` — skip/limit and
filter predicates are all pushed down to SQL.

| Query param | Type | Required | Default | Notes |
|-------------|------|----------|---------|-------|
| `source`    | string | no  | —    | Exact match. Max 255 chars. |
| `type`      | string | no  | —    | Exact match. Max 50 chars. |
| `from`      | ISO-8601 instant | no | — | Inclusive lower bound on `ingestedAt`. Accepts any offset (`Z`, `+02:00`, etc.); normalised to UTC at the persistence boundary. |
| `to`        | ISO-8601 instant | no | — | Inclusive upper bound on `ingestedAt`. |
| `sortBy`    | string | no  | `ingestedAt` | One of `ingestedAt`, `id`. Anything else → 400. |
| `sortDir`   | string | no  | `desc` | One of `asc`, `desc`. Anything else → 400. |
| `page`      | int    | no  | `0`   | Zero-indexed. Negative → 400. |
| `pageSize`  | int    | no  | `20`  | 1..500. Out of range → 400. |

**Deterministic paging.** `id` is always appended as a secondary sort in the
same direction as the primary. Without it, two records sharing an
`ingestedAt` timestamp could shuffle between pages; ingestion timestamps are
at sub-millisecond density, so collisions are not hypothetical.

**Empty result is 200, not 404.** A filter that matches zero records returns
`200` with `content: []` and `total_elements: 0`. The `total_elements` field
reflects the *filtered* count, never the whole table.

**Errors.** All 4xx responses share the envelope produced by
`GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-09-06T21:08:40.7906647Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid sortBy 'bogus'. Allowed values: [ingestedAt, id]"
}
```

Validation failures (`@Max`, `@Min`, `@Size`, unknown `sortBy`/`sortDir`,
`from > to`, malformed `Instant`) all produce this envelope via
`ConstraintViolationException` / `IllegalArgumentException` /
`MethodArgumentTypeMismatchException` handlers.

## Notes

- **Kafka** is disabled by default. When `app.kafka.enabled=false` (the default), a `NoOpKafkaProducer` bean is loaded and `publish()` calls are logged but produce no broker traffic — proven by `NoOpKafkaProducerIT`. Set `APP_KAFKA_ENABLED=true` (or `app.kafka.enabled: true`) to switch to the real `KafkaDataProducer`, which sends ingested records to the `dataops-raw-ingest` topic. An actual broker must be reachable for publishes to succeed; the end-to-end path is exercised by `KafkaEndToEndIT` (Testcontainers-backed, gated by `-DrunDockerIT=true`).
- The default database is file-backed H2; production uses Postgres via `docker-compose.yml`.
- `/api/v1/storage/export/binary` returns a custom binary stream produced by `BinaryRecordSerializer` (length-prefixed record frames). Round-trippable via `BinaryRecordSerializer.readRecord()`.
- Recent architectural improvements include persistence consolidation and enhanced error handling

## License

MIT. See [LICENSE](LICENSE).
