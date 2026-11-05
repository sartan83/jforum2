# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY jforum-boot/pom.xml jforum-boot/pom.xml
COPY tools/migration/pom.xml tools/migration/pom.xml
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl jforum-boot -am dependency:go-offline -DskipTests || true
COPY jforum-boot/src jforum-boot/src
COPY tools/migration/src tools/migration/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl jforum-boot -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 jforum
WORKDIR /app
COPY --from=build /src/jforum-boot/target/jforum.jar app.jar
USER jforum
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q UP || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
