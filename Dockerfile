FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY . .

RUN mvn -B -pl dataops-platform-monolith -am clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

ENV APP_KAFKA_ENABLED=false
ENV SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/dataops
ENV SPRING_DATASOURCE_DRIVER=org.h2.Driver
ENV SPRING_DATASOURCE_USERNAME=sa
ENV SPRING_DATASOURCE_PASSWORD=
ENV SPRING_JPA_DIALECT=org.hibernate.dialect.H2Dialect
ENV API_KEY=docker-default-key

RUN mkdir -p /app/data

COPY --from=build /workspace/dataops-platform-monolith/target/dataops-platform-monolith-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx1g", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
