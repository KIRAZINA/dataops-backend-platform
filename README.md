# DataOps Backend Platform
**High-performance data ingestion, storage & analytics engine**  
Java 17 · Spring Boot 3.3 · Multi-module Maven · Production-ready Monolith

## Features (exactly what Senior/Lead interviews look for)

| Feature                          | Implementation                                                | Enterprise level |
|----------------------------------|---------------------------------------------------------------|------------------|
| Ultra-fast in-memory storage     | Custom collections + RingBuffer                               | ★★★★★           |
| Persistence layer                | JPA + raw JDBC + Flyway migrations                            | ★★★★★           |
| File export                      | JSON + custom binary format + async non-blocking writing     | ★★★★★           |
| Analytics engine                 | Real-time aggregation + JMH micro-benchmarks                 | ★★★★★           |
| Kafka integration                | Graceful degradation with NoOp producer                      | ★★★★★           |
| API Gateway                      | OpenAPI 3 + Swagger UI + Rate limiting + CORS                | ★★★★★           |
| Cross-cutting concerns           | AOP logging (full request/response + duration)               | ★★★★★           |
| Global error handling            | `@RestControllerAdvice` + standardized JSON errors           | ★★★★★           |
| Observability                    | Spring Boot Actuator + Prometheus `/actuator/prometheus`      | ★★★★★           |

## Architecture (12 clean modules)
module-00-common-models          → DTOs & domain events
module-01-core                   → Custom collections & algorithms (with unit tests)
module-02-in-memory-engine       → Lightning-fast in-memory storage
module-03-persistence            → JPA + JDBC + Flyway
module-04-file-storage           → Async binary/JSON export with coordination primitives
module-05-analytics              → Aggregation engine + JMH sorting benchmarks
module-06-streaming-kafka        → Kafka publisher with NoOp fallback (graceful degradation)
module-07-api                    → Swagger UI, rate limiting, CORS, global exception handler
module-08-aop-logging            → Enterprise-grade request/response AOP logging
common-test                      → Shared test utilities (Testcontainers-ready)
dataops-platform-monolith        → Fat JAR executable (all-in-one)
text