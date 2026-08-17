# The Linux the roadmap asks for, without a Linux machine: everything jagt shells out to, installed for real.
#
# It exists because the drivers were written and wired from macOS. `notify-send` under a session bus, kitty's
# remote control under an X server, tmux windows on another kernel and git on overlayfs are all things only a
# real Linux can answer — and a container is a real Linux. What it CANNOT answer stays out of here rather than
# being faked: IntelliJ (`idea`), the macOS AppleScript raise, the Warp URI scheme, the actual `claude` CLI.
#
# Build:  docker build -f docker/linux-suite.Dockerfile -t jagt-linux-suite .
# Use:    scripts/linux-suite.sh   (mounts the repo, runs the suites inside)
FROM eclipse-temurin:25-jdk

# The package list lives in scripts/linux-test-deps.sh — the SAME list every CI runner installs, so the
# container and the pipeline cannot drift apart.
COPY scripts/linux-test-deps.sh /tmp/linux-test-deps.sh
RUN sh /tmp/linux-test-deps.sh && rm /tmp/linux-test-deps.sh

# A committer identity: the deploy/revert flows make real commits, and git refuses without one.
RUN git config --global user.email "linux-suite@example.com" \
    && git config --global user.name "jagt linux suite" \
    && git config --global init.defaultBranch main \
    # The repo is bind-mounted from the host, so its owner uid differs from the container user.
    && git config --global --add safe.directory '*'

# Gradle's caches live on a named volume (see the script), so a rerun does not re-download the distribution.
# The board suite's browser goes on the same volume, or every run pays for it again.
ENV GRADLE_USER_HOME=/gradle-home
ENV PLAYWRIGHT_BROWSERS_PATH=/gradle-home/ms-playwright
ENV JAGT_IN_CONTAINER=1

WORKDIR /jagt/orchestrator-backend
