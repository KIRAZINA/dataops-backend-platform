# DataOps Backend Platform

![Java 17](https://img.shields.io/badge/Java-17-blue.svg)
![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![CI](https://github.com/KIRAZINA/dataops-backend-platform/actions/workflows/maven.yml/badge.svg)

**Production-ready multi-module monolith** — Senior / Lead / Principal Engineer level.

12 clean modules, event-driven architecture, full enterprise feature set, and one-command launch.

## Overview

DataOps Backend Platform is a comprehensive, production-ready monolithic application designed for data ingestion, processing, analytics, and storage. Built with modern Java technologies, it provides a scalable solution for handling various data formats with robust error handling and monitoring capabilities.

## Features

| Feature                              | Implementation                                           | Status |
|--------------------------------------|----------------------------------------------------------|--------|
| In-memory storage + indexes          | ConcurrentHashMap + HashMap for O(1) ID lookup + custom events | Done   |
| Persistence                          | H2 + Flyway + JSON column                                | Done   |
| File export                          | JSON + CSV (proper filenames, correct escaping)          | Done   |
| CSV multi-row support                | Full CSV parsing with multiple record ingestion          | Done   |
| Analytics                            | Grouping, averages, custom sorts (quick/merge/heap)      | Done   |
| Sorting benchmarks                   | JMH (1K → 10K → 1M elements)                            | Done   |
| Kafka integration                    | Graceful degradation + NoOp fallback                     | Done   |
| Rate limiting                        | Caffeine-based (100 req/min + X-RateLimit-* headers)     | Done   |
| AOP logging                          | Method entry/exit + execution time (safe, no heavy serialization) | Done   |
| Enhanced error handling              | Proper exception handling with meaningful error responses | Done   |
| Enhanced logging                     | Comprehensive logging for all operations and errors      | Done   |
| OpenAPI + Swagger UI                 | Full interactive documentation + CORS                    | Done   |
| Metrics                              | Actuator + Prometheus                                    | Done   |
| One-command launch                   | `run.bat` / `run.sh` / `mvn spring-boot:run`             | Done   |

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+

### Running the Application

```bash
# Windows
.\run.bat

# Linux / macOS / Git Bash
./run.sh

# Or universally via Maven
mvn spring-boot:run -pl dataops-platform-monolith
```

After starting, navigate to [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) to access the interactive API documentation.

## API Endpoints

### Data Ingestion

| Method | Path                                 | Description                                  |
|--------|--------------------------------------|----------------------------------------------|
| POST   | `/api/v1/ingest/json`                | Ingest JSON payload → in-memory + DB         |
| POST   | `/api/v1/ingest/csv`                 | Ingest CSV payload (multi-row support) → in-memory + DB |
| POST   | `/api/v1/ingest/xml`                 | Ingest XML payload → in-memory + DB          |
| POST   | `/api/v1/ingest/file`                | Upload and ingest file (JSON/CSV/XML) → in-memory + DB |
| GET    | `/api/v1/ingest/{id}`                | Retrieve record by ID                        |
| GET    | `/api/v1/ingest/source/{source}`     | Retrieve records by source                   |
| GET    | `/api/v1/ingest/type/{type}`         | Retrieve records by type                     |

### Analytics

| Method | Path                                 | Description                                  |
|--------|--------------------------------------|----------------------------------------------|
| GET    | `/api/v1/analytics/stats`            | Aggregated statistics                        |
| GET    | `/api/v1/analytics/sorted`           | Sorted records (quicksort by default)        |

### Export & Monitoring

| Method | Path                                 | Description                                  |
|--------|--------------------------------------|----------------------------------------------|
| GET    | `/api/v1/storage/export/json`        | Download all records as JSON                 |
| GET    | `/api/v1/storage/export/csv`         | Download all records as CSV                  |
| GET    | `/actuator/prometheus`               | Prometheus metrics                           |
| GET    | `/swagger-ui.html`                   | Interactive OpenAPI UI                       |

## Architecture

### Project Structure

```
dataops-backend-platform
├─ module-00-common-models          # Shared models and events
├─ module-01-core                   # Core utilities and custom collections
├─ module-02-in-memory-engine       # In-memory data processing engine
├─ module-03-persistence            # Data persistence layer (H2 + JPA)
├─ module-04-file-storage           # File storage and distributed coordination
├─ module-05-analytics              # Analytics engine and processing
├─ module-06-streaming-kafka        # Kafka streaming integration
├─ module-07-api                    # API gateway and documentation
├─ module-08-aop-logging            # AOP-based logging
├─ common-test                      # Common test utilities
└─ dataops-platform-monolith        # Main executable monolith
```

### Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.3.4
- **Persistence**: Spring Data JPA + Hibernate + H2 Database
- **Migration**: Flyway
- **Caching**: Caffeine Cache
- **Monitoring**: Micrometer + Prometheus + Actuator
- **Messaging**: Kafka (with graceful degradation)
- **Documentation**: springdoc OpenAPI + Swagger UI
- **Build Tool**: Maven (multi-module)
- **Utilities**: Lombok, Jackson
- **Testing**: JUnit 5, Mockito
- **CI/CD**: GitHub Actions

## Key Improvements

### Performance Enhancements
- **O(1) ID Lookup**: Implemented HashMap-based indexing for constant-time record retrieval
- **Multi-row CSV Processing**: Enhanced CSV parser to handle multiple records in a single file
- **Optimized Batch Operations**: Efficient bulk data ingestion capabilities

### Reliability & Error Handling
- **Comprehensive Error Handling**: Global exception handler with structured error responses
- **Robust Logging**: Detailed logging for all operations and error conditions
- **Input Validation**: Proper validation and sanitization of incoming data

### Developer Experience
- **Structured Error Responses**: Consistent error format with timestamps and details
- **Enhanced Documentation**: Updated API documentation and examples
- **Improved Testability**: Better separation of concerns for easier testing

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
