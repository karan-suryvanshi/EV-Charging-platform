# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw
RUN chmod +x mvnw
RUN --mount=type=cache,id=evcharging-maven-repo,target=/root/.m2,sharing=locked \
    ./mvnw -B -U -Daether.transport.http.retryHandler.count=5 -DskipTests dependency:go-offline
COPY src src
RUN --mount=type=cache,id=evcharging-maven-repo,target=/root/.m2,sharing=locked \
    ./mvnw -B -U -Daether.transport.http.retryHandler.count=5 -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/ev-charging-backend-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
