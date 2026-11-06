# syntax=docker/dockerfile:1

# ---- build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Optional Maven Central mirror (e.g. when repo.maven.apache.org rate limits the build host)
ARG MAVEN_MIRROR_URL=
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
      printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /usr/share/maven/conf/settings.xml; \
    fi

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline -DskipTests || true

COPY src src
COPY templates templates
COPY images images
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/jforum.jar /workspace/app.jar

# ---- runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system jforum && useradd --system --gid jforum --home-dir /opt/jforum jforum \
    && mkdir -p /opt/jforum/home && chown -R jforum:jforum /opt/jforum
WORKDIR /opt/jforum
COPY --from=build /workspace/app.jar app.jar
USER jforum

ENV JFORUM_HOME=/opt/jforum/home \
    SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/jforum \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/jforum/app.jar"]
