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

# git + tmux: what the task flow itself runs. node: the MCP proxy every worktree symlinks.
# kitty + xvfb + dbus + dunst + libnotify: the two Linux DRIVERS, tested against the real binaries.
# procps/lsof: liveness probing and the language-server reaping both read the process table.
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        git \
        tmux \
        nodejs \
        kitty \
        xvfb \
        dbus-x11 \
        dunst \
        libnotify-bin \
        procps \
        lsof \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# A committer identity: the deploy/revert flows make real commits, and git refuses without one.
RUN git config --global user.email "linux-suite@example.com" \
    && git config --global user.name "jagt linux suite" \
    && git config --global init.defaultBranch main \
    # The repo is bind-mounted from the host, so its owner uid differs from the container user.
    && git config --global --add safe.directory '*'

# Gradle's caches live on a named volume (see the script), so a rerun does not re-download the distribution.
ENV GRADLE_USER_HOME=/gradle-home
ENV JAGT_IN_CONTAINER=1

WORKDIR /jagt/orchestrator-backend
