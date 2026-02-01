```markdown
# DataOps Backend Platform

![Java 17](https://img.shields.io/badge/Java-17-blue.svg)
![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![CI](https://github.com/KIRAZINA/dataops-backend-platform/actions/workflows/maven.yml/badge.svg)

**Production-ready multi-module monolith** — Senior / Lead / Principal Engineer level.

12 clean modules, event-driven architecture, full enterprise feature set, and one-command launch.

### Features

| Feature                              | Implementation                                           | Status |
|--------------------------------------|------------------------------------------------------------------|--------|
| In-memory storage + indexes          | ConcurrentHashMap + custom events + HashMap for ID lookup          | Done   |
| Persistence                          | H2 + Flyway + JSON column                                        | Done   |
| File export                          | JSON + CSV (proper filenames, correct escaping)                  | Done   |
| CSV multi-row support                | Full CSV parsing with multiple record ingestion                    | Done   |
| Analytics                            | Grouping, averages, custom sorts (quick/merge/heap)              | Done   |
| Sorting benchmarks                   | JMH (1K → 10K → 1M elements)                                    | Done   |
| Kafka integration                    | Graceful degradation + NoOp fallback                             | Done   |
| Rate limiting                        | Caffeine-based (100 req/min + X-RateLimit-* headers)             | Done   |
| AOP logging                          | Method entry/exit + execution time (safe, no heavy serialization)| Done   |
| Enhanced logging                     | Comprehensive logging for all operations and errors              | Done   |
| Improved error handling              | Proper exception handling with meaningful error responses          | Done   |
| OpenAPI + Swagger UI                 | Full interactive documentation + CORS                            | Done   |
| Metrics                              | Actuator + Prometheus                                            | Done   |
| One-command launch                   | `run.bat` / `run.sh` / `mvn spring-boot:run`                     | Done   |

### Quick Start (≤ 12 seconds)

```bash
# Windows
.\run.bat

# Linux / macOS / Git Bash
./run.sh

# Or universally via Maven
mvnw spring-boot:run -pl dataops-platform-monolith
```

→ Open http://localhost:8080/swagger-ui.html

### Main Endpoints

| Method | Path                                 | Description                                  |
|--------|--------------------------------------|----------------------------------------------|
| POST   | `/api/v1/ingest/json`                | Ingest JSON payload → in-memory + DB         |
| POST   | `/api/v1/ingest/csv`                 | Ingest CSV payload (multi-row support) → in-memory + DB |
| POST   | `/api/v1/ingest/xml`                 | Ingest XML payload → in-memory + DB          |
| POST   | `/api/v1/ingest/file`                | Upload and ingest file (JSON/CSV/XML) → in-memory + DB |
| GET    | `/api/v1/analytics/stats`            | Aggregated statistics                        |
| GET    | `/api/v1/analytics/sorted`           | Sorted records (quicksort by default)        |
| GET    | `/api/v1/storage/export/json`        | Download all records as JSON                 |
| GET    | `/api/v1/storage/export/csv`         | Download all records as CSV                  |
| GET    | `/api/v1/ingest/{id}`                | Retrieve record by ID                        |
| GET    | `/api/v1/ingest/source/{source}`     | Retrieve records by source                   |
| GET    | `/api/v1/ingest/type/{type}`         | Retrieve records by type                     |
| GET    | `/actuator/prometheus`               | Prometheus metrics                           |
| GET    | `/swagger-ui.html`                   | Beautiful OpenAPI UI                         |

### Project Structure

```
dataops-backend-platform
├─ module-00-common-models
├─ module-01-core
├─ module-02-in-memory-engine
├─ module-03-persistence
├─ module-04-file-storage
├─ module-05-analytics
├─ module-06-streaming-kafka
├─ module-07-api
├─ module-08-aop-logging
├─ common-test
└─ dataops-platform-monolith (executable fat JAR)
```

### Tech Stack

- Java 17
- Spring Boot 3.3.4
- Spring Data JPA + Hibernate
- Flyway
- H2 Database
- Caffeine Cache
- Micrometer + Prometheus
- Kafka (optional with graceful degradation
- Lombok
- Jackson
- springdoc OpenAPI
- Maven multi-module
- GitHub Actions CI
