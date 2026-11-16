FROM eclipse-temurin:25-jdk-noble AS build
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
ENV GIT_AUTHOR_NAME=build GIT_AUTHOR_EMAIL=build@localhost GIT_COMMITTER_NAME=build GIT_COMMITTER_EMAIL=build@localhost
RUN git init -q && git commit -q --allow-empty -m build
WORKDIR /src/orchestrator-backend
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre-noble
RUN apt-get update \
 && apt-get install -y --no-install-recommends git tmux tini procps lsof openssh-client \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --create-home --uid 10001 jagt \
 && mkdir -p /app /data \
 && chown jagt /data
WORKDIR /app
COPY --from=build /src/orchestrator-backend/build/libs/jagt.jar jagt.jar
COPY jagt.desktop.yml.dist mcp_client.js ./
COPY entrypoint.sh ./
COPY roles roles
COPY jagt.server.yml.dist jagt.yml
ENV ORCHESTRATOR_ROOT=/app ORCHESTRATOR_DATA=/data
USER jagt
EXPOSE 8290
ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]
