ARG BASE=ghcr.io/guldmitry/jagt-base:base@sha256:fe607a00ebd2dd623b200fd41bcad94971719f75988a6cabf69d2ee86747a1fa
FROM eclipse-temurin:25-jdk-noble@sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f AS build
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
ENV GIT_AUTHOR_NAME=build GIT_AUTHOR_EMAIL=build@localhost GIT_COMMITTER_NAME=build GIT_COMMITTER_EMAIL=build@localhost
RUN git init -q && git commit -q --allow-empty -m build
WORKDIR /src/orchestrator-backend
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon

# A context named jar (docker build --build-context jar=<dir holding jagt.jar>) overrides this stage, so no Gradle runs here.
FROM build AS jar
RUN cp /src/orchestrator-backend/build/libs/jagt.jar /jagt.jar

FROM ${BASE}
USER root
WORKDIR /app
COPY --from=jar /jagt.jar jagt.jar
COPY jagt.desktop.yml.dist mcp_client.js ./
COPY entrypoint.sh ./
COPY roles roles
COPY jagt.server.yml.dist jagt.seed.yml
RUN chgrp -R 0 /app /data && chmod -R g=u /app /data /etc/passwd
ENV HOME=/data/home ORCHESTRATOR_ROOT=/app ORCHESTRATOR_DATA=/data ORCHESTRATOR_CONFIG_FILE=/data/jagt.yml
ENV JAGT_JAVA_OPTIONS="-Xmx512m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/data/dumps"
USER 10001:0
EXPOSE 8290
ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]
