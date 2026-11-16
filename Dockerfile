FROM eclipse-temurin:25-jdk-noble@sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f AS build
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
ENV GIT_AUTHOR_NAME=build GIT_AUTHOR_EMAIL=build@localhost GIT_COMMITTER_NAME=build GIT_COMMITTER_EMAIL=build@localhost
RUN git init -q && git commit -q --allow-empty -m build
WORKDIR /src/orchestrator-backend
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre-noble@sha256:e3b6f7887a50f20d8f07abf279d8a81a5953fb398a9555bd9edd56b9bd15968c
RUN apt-get update \
 && apt-get install -y --no-install-recommends git tmux tini procps lsof openssh-client \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --gid 10001 jagt \
 && useradd --create-home --uid 10001 --gid 10001 jagt \
 && mkdir -p /app /data \
 && chown jagt /data
WORKDIR /app
COPY --from=build /src/orchestrator-backend/build/libs/jagt.jar jagt.jar
COPY jagt.desktop.yml.dist mcp_client.js ./
COPY entrypoint.sh ./
COPY roles roles
COPY jagt.server.yml.dist jagt.yml
ENV ORCHESTRATOR_ROOT=/app ORCHESTRATOR_DATA=/data
ENV JAGT_JAVA_OPTIONS="-XX:MaxRAMPercentage=25 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/data/dumps"
USER jagt
EXPOSE 8290
ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]
