# A CI runner without a runner: Ubuntu 24.04, the JDK, Node, Docker and everything jagt shells out to.
# Use:    scripts/linux-suite.sh <job…>   (mounts the repo, runs each job's gate inside)
FROM node:22.23.5-bookworm-slim@sha256:e9f565d32859f0cbfb52125eafe8be39390f24dac302dc3cdf566e8ba3ab21ee AS node

FROM eclipse-temurin:25-jdk@sha256:ce2ef9d2d67a68de5e196d4de9d743327c44a10b57fe02ec39f572c652cacfd3

COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# The package list lives in scripts/linux-test-deps.sh — the SAME list every CI runner installs, so the
# container and the pipeline cannot drift apart.
COPY scripts/linux-test-deps.sh /tmp/linux-test-deps.sh
RUN sh /tmp/linux-test-deps.sh && rm /tmp/linux-test-deps.sh

# A Docker engine of its own, as a CI runner has one: the image under compose and the compose stand run on it.
RUN apt-get update && apt-get install -y --no-install-recommends docker.io docker-compose-v2 iptables \
    && rm -rf /var/lib/apt/lists/*

# A committer identity: the deploy/revert flows make real commits, and git refuses without one.
RUN git config --system user.email "linux-suite@example.com" \
    && git config --system user.name "jagt linux suite" \
    && git config --system init.defaultBranch main \
    # The repo is bind-mounted from the host, so its owner uid differs from the container user.
    && git config --system --add safe.directory '*'

# Gradle's caches live on a named volume (see the script), so a rerun does not re-download the distribution.
# The board suite's browser goes on the same volume, or every run pays for it again.
ENV GRADLE_USER_HOME=/gradle-home
ENV PLAYWRIGHT_BROWSERS_PATH=/gradle-home/ms-playwright
ENV JAGT_IN_CONTAINER=1

WORKDIR /jagt/orchestrator-backend
