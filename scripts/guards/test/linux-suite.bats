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
@test "a job's gate runs with no network after a warm-up that has one" {
  run "$script" test
  [ "$status" -eq 0 ]
  [ "$(grep -c -- '--network none' "$DOCKER_LOG")" -eq 1 ]
  [ "$(grep -- '--network none' "$DOCKER_LOG" | grep -c gateTest)" -eq 1 ]
  [ "$(grep -c resolveAll "$DOCKER_LOG")" -eq 1 ]
}

@test "the board job installs its browser online and leaves it out offline" {
  run "$script" boardTest
  [ "$status" -eq 0 ]
  grep resolveAll "$DOCKER_LOG" | grep -q playwrightChromium
  grep -- '--network none' "$DOCKER_LOG" | grep -q -- '-x playwrightChromium'
}

@test "the stand job pulls the images it runs in its online warm-up" {
  run "$script" stand
  grep resolveAll "$DOCKER_LOG" | grep -q ':stand:standPull'
}

@test "only the kind stand installs the kind tools, in its own run" {
  run "$script" nightly-stand-kind stand
  grep gateStand "$DOCKER_LOG" | grep -- '-Pengine=kind' | grep -q -- '-e KIND_TOOLS=1 '
  grep gateStand "$DOCKER_LOG" | grep -- '-Pengine=compose' | grep -q -- '-e KIND_TOOLS=0 '
}

@test "the offline container still resolves its own hostname" {
  run "$script" test
  grep -- '--network none' "$DOCKER_LOG" | grep -q -- '--hostname runner --add-host runner:127.0.0.1'
}

@test "the container is never capped in CPU or memory" {
  run "$script" test
  ! grep -q -- '--\(cpus\|memory\)' "$DOCKER_LOG"
}

@test "a cache path on the host holds the shared Gradle home in place of a volume" {
  JAGT_GRADLE_CACHE="$BATS_TEST_TMPDIR/gradle-home" run "$script" test
  [ -d "$BATS_TEST_TMPDIR/gradle-home" ]
  grep -q -- "-v $BATS_TEST_TMPDIR/gradle-home:/gradle-ro:ro" "$DOCKER_LOG"
  ! grep -q 'volume create jagt-gradle-home' "$DOCKER_LOG"
}

@test "on a Linux host the job runs as the checkout's owner" {
  printf '#!/bin/sh\necho Linux\n' > "$BATS_TEST_TMPDIR/bin/uname"
  printf '#!/bin/sh\necho 1001\n' > "$BATS_TEST_TMPDIR/bin/stat"
  chmod +x "$BATS_TEST_TMPDIR/bin/uname" "$BATS_TEST_TMPDIR/bin/stat"
  run "$script" stand
  [ "$status" -eq 0 ]
  grep -- '--network none' "$DOCKER_LOG" | grep -q -- '-e RUN_UID=1001 '
}

@test "the test job runs as root, as ci.yml's test container does" {
  printf '#!/bin/sh\necho Darwin\n' > "$BATS_TEST_TMPDIR/bin/uname"
  chmod +x "$BATS_TEST_TMPDIR/bin/uname"
  run "$script" test heavy
  grep -- '--network none' "$DOCKER_LOG" | grep gateTest | grep -q -- '-e RUN_UID=0 '
  grep -- '--network none' "$DOCKER_LOG" | grep gateHeavy | grep -vq -- '-e RUN_UID=0 '
}

@test "on a Mac the job runs as a hosted runner's uid" {
  printf '#!/bin/sh\necho Darwin\n' > "$BATS_TEST_TMPDIR/bin/uname"
  chmod +x "$BATS_TEST_TMPDIR/bin/uname"
  run "$script" stand
  grep -- '--network none' "$DOCKER_LOG" | grep -q -- '-e RUN_UID=1001 '
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

@test "a job's live steps run in the warm-up and are left out offline" {
  run "$script" test
  grep -- 'resolveAll' "$DOCKER_LOG" | grep -q secretScan
  grep -- '--network none' "$DOCKER_LOG" | grep -q -- '-x secretScan'
}

# bats test_tags=exit:0
@test "the audit job runs the live scans online against the main branch" {
  run "$script" audit
  [ "$status" -eq 0 ]
  [ "$(grep -c -- '--network none' "$DOCKER_LOG")" -eq 0 ]
  [ "$(grep -c -- 'gateScan -Paudit.base=origin/main' "$DOCKER_LOG")" -eq 1 ]
}

@test "the test job no longer carries the live vulnerability scan" {
  run "$script" test
  [ "$(grep -c vulnerabilityScan "$DOCKER_LOG")" -eq 0 ]
}

@test "two suites at once each get a Gradle home of their own" {
  "$script" test &
  first=$!
  "$script" test &
  second=$!
  wait "$first" "$second"
  homes=$(grep -- '--network none' "$DOCKER_LOG" | grep -oE 'jagt-gradle-run-[0-9]+:/gradle-home' | sort -u)
  [ "$(echo "$homes" | wc -l)" -eq 2 ]
}

@test "a run starts and removes an engine of its own" {
  run "$script" test
  grep -q 'volume create jagt-ci-docker-[0-9]' "$DOCKER_LOG"
  grep -q 'volume rm -f jagt-gradle-run-[0-9]* jagt-ci-docker-[0-9]' "$DOCKER_LOG"
}

@test "a run removes the Gradle home it made" {
  run "$script" test
  grep -qE 'volume rm -f jagt-gradle-run-[0-9]+' "$DOCKER_LOG"
}

@test "the install job keeps its network for the Java runtime feed" {
  run "$script" install
  [ "$(grep -c -- '--network none' "$DOCKER_LOG")" -eq 0 ]
}

@test "a nightly job runs its nightly line with no network after its warm-up" {
  run "$script" nightly-properties
  [ "$status" -eq 0 ]
  [ "$(grep -c -- '--network none' "$DOCKER_LOG")" -eq 1 ]
  grep -- '--network none' "$DOCKER_LOG" | grep -q 'gateProperties -PjqwikTries=1000 --rerun-tasks'
  [ "$(grep -c resolveAll "$DOCKER_LOG")" -eq 1 ]
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

@test "the nightly stand job warms up the images the stand runs" {
  run "$script" nightly-stand-compose
  grep resolveAll "$DOCKER_LOG" | grep -q ':stand:standPull'
  grep -- '--network none' "$DOCKER_LOG" | grep -q -- '-Pengine=compose -PstandRuns=3 --rerun-tasks'
}

@test "the nightly kind and scan jobs keep their network for the registry and the live data" {
  run "$script" nightly-stand-kind nightly-scan
  [ "$status" -eq 0 ]
  [ "$(grep -c -- '--network none' "$DOCKER_LOG")" -eq 0 ]
  grep -q -- '-Pengine=kind -PstandRuns=3' "$DOCKER_LOG"
  grep -q -- 'gateScan' "$DOCKER_LOG"
}

@test "the runner moves its processes into an init cgroup before dockerd starts" {
  run "$script" nightly-stand-kind
  grep -q 'cgroup.procs >/sys/fs/cgroup/init/cgroup.procs.*dockerd >/tmp/dockerd.log' "$DOCKER_LOG"
}

# bats test_tags=exit:2
@test "an unknown job is refused before Docker is touched" {
  run "$script" nonsense
  [ "$status" -eq 2 ]
  [ ! -e "$DOCKER_LOG" ]
}
