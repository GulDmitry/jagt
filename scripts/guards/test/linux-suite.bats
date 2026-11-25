script="$BATS_TEST_DIRNAME/../../../orchestrator-backend/scripts/linux-suite.sh"

setup() {
  mkdir "$BATS_TEST_TMPDIR/bin"
  cat > "$BATS_TEST_TMPDIR/bin/docker" <<'EOF'
#!/bin/sh
printf '%s\n' "$(echo "$*" | tr '\n' ' ')" >> "$DOCKER_LOG"
[ "$1" = run ] && sleep 1
exit 0
EOF
  chmod +x "$BATS_TEST_TMPDIR/bin/docker"
  export PATH="$BATS_TEST_TMPDIR/bin:$PATH" DOCKER_LOG="$BATS_TEST_TMPDIR/docker.log"
  unset GITHUB_ACTIONS JAGT_GRADLE_CACHE
}

# bats test_tags=exit:0
@test "a job runs its gate once, with its network" {
  run "$script" test
  [ "$status" -eq 0 ]
  [ "$(grep -c 'gateTest' "$DOCKER_LOG")" -eq 1 ]
}

@test "no job is cut off from the network, and none warms up first" {
  run "$script" test heavy stand stand-kind install audit boardTest nightly-stand-compose nightly-stand-kind nightly-e2e nightly-properties nightly-scan
  [ "$status" -eq 0 ]
  ! grep -q -- '--network\|resolveAll\|npm_config_offline\|GRADLE_RO_DEP_CACHE' "$DOCKER_LOG"
}

@test "a job's live steps run inside its gate, none left out" {
  run "$script" test
  grep -q 'gateTest' "$DOCKER_LOG"
  ! grep -q -- ' -x ' "$DOCKER_LOG"
}

@test "the board job runs its own line" {
  run "$script" boardTest
  grep -q 'boardInstall boardTest' "$DOCKER_LOG"
}

@test "only the kind stands install the kind tools, in their own run" {
  run "$script" nightly-stand-kind stand-kind stand
  [ "$(grep gateStand "$DOCKER_LOG" | grep -- '-Pengine=kind' | grep -c -- '-e KIND_TOOLS=1 ')" -eq 2 ]
  grep gateStand "$DOCKER_LOG" | grep -- '-Pengine=compose' | grep -q -- '-e KIND_TOOLS=0 '
}

@test "the container is never capped in CPU or memory" {
  run "$script" test
  ! grep -q -- '--\(cpus\|memory\)' "$DOCKER_LOG"
}

@test "a cache path on the host holds the speed cache in place of a volume" {
  JAGT_GRADLE_CACHE="$BATS_TEST_TMPDIR/gradle-home" run "$script" test
  [ -d "$BATS_TEST_TMPDIR/gradle-home" ]
  grep -q -- "-v $BATS_TEST_TMPDIR/gradle-home:/gradle-home " "$DOCKER_LOG"
  ! grep -q 'volume create jagt-gradle-home' "$DOCKER_LOG"
}

@test "the speed cache is writable by the job, never a read-only seed" {
  run "$script" test
  grep -q -- '-v jagt-gradle-home-test:/gradle-home ' "$DOCKER_LOG"
  ! grep -q -- '/gradle-ro' "$DOCKER_LOG"
}

@test "each job has a speed cache of its own, which two runs of it take turns on" {
  run "$script" test heavy
  grep -q 'volume create jagt-gradle-home-test' "$DOCKER_LOG"
  grep -q 'volume create jagt-gradle-home-heavy' "$DOCKER_LOG"
  grep -q 'exec 8>/gradle-home/.run.lock[[:space:]]*flock 8' "$DOCKER_LOG"
}

@test "on a Linux host the job runs as the checkout's owner" {
  printf '#!/bin/sh\necho Linux\n' > "$BATS_TEST_TMPDIR/bin/uname"
  printf '#!/bin/sh\necho 1001\n' > "$BATS_TEST_TMPDIR/bin/stat"
  chmod +x "$BATS_TEST_TMPDIR/bin/uname" "$BATS_TEST_TMPDIR/bin/stat"
  run "$script" stand
  [ "$status" -eq 0 ]
  grep gateStand "$DOCKER_LOG" | grep -q -- '-e RUN_UID=1001 '
}

@test "on a Mac every job runs as a hosted runner's uid, the test job included" {
  printf '#!/bin/sh\necho Darwin\n' > "$BATS_TEST_TMPDIR/bin/uname"
  chmod +x "$BATS_TEST_TMPDIR/bin/uname"
  run "$script" test heavy
  [ "$(grep -c -- '-e RUN_UID=1001 ' "$DOCKER_LOG")" -eq 2 ]
  ! grep -q -- '-e RUN_UID=0 ' "$DOCKER_LOG"
}

@test "the gate passes no concurrency of its own to Gradle" {
  run "$script" stand
  ! grep -q -- '--max-workers\|-PstandParallelism\|-Pe2eForks' "$DOCKER_LOG"
}

@test "the job line names the job and its command, not CPUs or memory" {
  run "$script" test
  [[ "$output" == *"==> test: ./gradlew"* ]]
  [[ "$output" != *"CPUs"* ]]
}

# bats test_tags=exit:0
@test "the audit job runs the live scans against the main branch" {
  run "$script" audit
  [ "$status" -eq 0 ]
  [ "$(grep -c -- 'gateScan -Paudit.base=origin/main' "$DOCKER_LOG")" -eq 1 ]
}

@test "the test job no longer carries the live vulnerability scan" {
  run "$script" test
  [ "$(grep -c vulnerabilityScan "$DOCKER_LOG")" -eq 0 ]
}

@test "a run starts and removes an engine of its own" {
  run "$script" test
  grep -q 'volume create jagt-ci-docker-[0-9]' "$DOCKER_LOG"
  grep -q 'volume rm -f jagt-ci-docker-[0-9]' "$DOCKER_LOG"
}

# bats test_tags=exit:0
@test "a nightly job runs its nightly line" {
  run "$script" nightly-properties
  [ "$status" -eq 0 ]
  grep -q 'gateProperties -PjqwikTries=1000 --rerun-tasks' "$DOCKER_LOG"
}

@test "no job mutates: mutation runs only on a commit's diff, locally" {
  run "$script" nightly-mutation
  [ "$status" -eq 2 ]
  run "$script" nightly-mutate-board
  [ "$status" -eq 2 ]
  [ ! -e "$DOCKER_LOG" ]
  run grep -c -i 'pitest\|stryker\|gate[M]utation\|boardMutate' "$script"
  [ "$output" -eq 0 ]
}

@test "the nightly stand job runs its three runs on compose" {
  run "$script" nightly-stand-compose
  grep -q -- '-Pengine=compose -PstandRuns=3' "$DOCKER_LOG"
}

@test "each job's line carries only the flags that pay off" {
  run "$script" test heavy stand stand-kind install audit boardTest nightly-stand-compose nightly-stand-kind nightly-e2e nightly-properties nightly-scan
  [ "$status" -eq 0 ]
  [[ "$output" == *"==> test: ./gradlew --no-daemon gateTest"$'\n'* ]]
  [[ "$output" == *"==> heavy: scripts/with-linux-desktop.sh ./gradlew --no-daemon gateHeavy"$'\n'* ]]
  [[ "$output" == *"==> stand: ./gradlew --no-daemon gateStand -Pmodelfree -Pengine=compose"$'\n'* ]]
  [[ "$output" == *"==> stand-kind: ./gradlew --no-daemon gateStand -Pmodelfree -Pengine=kind"$'\n'* ]]
  [[ "$output" == *"==> install: ./gradlew --no-daemon gateInstall"$'\n'* ]]
  [[ "$output" == *"==> audit: ./gradlew --no-daemon gateScan -Paudit.base=origin/main"$'\n'* ]]
  [[ "$output" == *"==> boardTest: ./gradlew --no-daemon boardInstall boardTest"$'\n'* ]]
  [[ "$output" == *"==> nightly-stand-compose: ./gradlew --no-daemon gateStand -Pmodelfree -Pengine=compose -PstandRuns=3"$'\n'* ]]
  [[ "$output" == *"==> nightly-stand-kind: ./gradlew --no-daemon gateStand -Pmodelfree -Pengine=kind -PstandRuns=3"$'\n'* ]]
  [[ "$output" == *"==> nightly-e2e: ./gradlew --no-daemon gateRepeat"$'\n'* ]]
  [[ "$output" == *"==> nightly-properties: ./gradlew --no-daemon gateProperties -PjqwikTries=1000 --rerun-tasks"$'\n'* ]]
  [[ "$output" == *"==> nightly-scan: ./gradlew --no-daemon gateScan"* ]]
  [ "$(grep -c -- '--rerun-tasks' "$DOCKER_LOG")" -eq 1 ]
}

@test "the runner delegates the cgroup controllers by moby's retry loop before dockerd starts" {
  run "$script" stand-kind
  grep -qE 'while ! \{[[:space:]]+xargs -rn1 </sys/fs/cgroup/cgroup.procs >/sys/fs/cgroup/init/cgroup.procs \|\| :.*cgroup.subtree_control[[:space:]]+\}; do[[:space:]]+true[[:space:]]+done.*dockerd >/tmp/dockerd.log' "$DOCKER_LOG"
}

@test "the stand's evidence is copied out of the container's build volumes to the host" {
  run "$script" stand-kind
  grep -q -- '-v .*/stand/build:/evidence ' "$DOCKER_LOG"
  grep -q 'stand/failed test-results/standTest' "$DOCKER_LOG"
}

# bats test_tags=exit:2
@test "an unknown job is refused before Docker is touched" {
  run "$script" nonsense
  [ "$status" -eq 2 ]
  [ ! -e "$DOCKER_LOG" ]
}
