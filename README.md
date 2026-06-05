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

Windows:

```powershell
.\run.bat
```

Linux / macOS / Git Bash:

```bash
./run.sh
```

Maven Wrapper:

```bash
./mvnw -pl dataops-platform-monolith spring-boot:run
```

Windows Maven Wrapper:

```powershell
.\mvnw.cmd -pl dataops-platform-monolith spring-boot:run
```

### Docker

```bash
docker build -t dataops-platform .
docker run --rm -p 8080:8080 dataops-platform
```

Or:

```bash
docker compose up --build
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

## Notes

- Kafka is disabled by default and falls back to a no-op producer (enable via application.yml)
- The default database is file-backed H2
- Binary export is currently a placeholder endpoint
- Recent architectural improvements include persistence consolidation and enhanced error handling

## License

MIT. See [LICENSE](LICENSE).
