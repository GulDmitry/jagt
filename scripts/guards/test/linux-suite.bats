script="$BATS_TEST_DIRNAME/../../../orchestrator-backend/scripts/linux-suite.sh"

setup() {
  mkdir "$BATS_TEST_TMPDIR/bin"
  cat > "$BATS_TEST_TMPDIR/bin/docker" <<'EOF'
#!/bin/sh
printf '%s\n' "$(echo "$*" | tr '\n' ' ')" >> "$DOCKER_LOG"
case "$*" in *NCPU*) echo "${FAKE_CPUS:-8}" ;; *MemTotal*) echo "${FAKE_MEM:-34359738368}" ;; esac
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

@test "the offline container still resolves its own hostname" {
  run "$script" test
  grep -- '--network none' "$DOCKER_LOG" | grep -q -- '--hostname runner --add-host runner:127.0.0.1'
}

@test "the container is capped to the runner's size" {
  run "$script" test
  grep -q -- '--cpus 1.25 --memory 16g --memory-swap 16g' "$DOCKER_LOG"
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

@test "on a Mac the job runs as the image's user" {
  printf '#!/bin/sh\necho Darwin\n' > "$BATS_TEST_TMPDIR/bin/uname"
  chmod +x "$BATS_TEST_TMPDIR/bin/uname"
  run "$script" stand
  grep -- '--network none' "$DOCKER_LOG" | grep -q -- '-e RUN_UID=1000 '
}

@test "a hosted runner keeps every CPU it has" {
  GITHUB_ACTIONS=true FAKE_CPUS=4 run "$script" test
  grep -q -- '--cpus 4 ' "$DOCKER_LOG"
}

@test "a host with fewer CPUs than the quota caps the container to the host" {
  FAKE_CPUS=0.5 run "$script" test
  grep -q -- '--cpus 0.5 ' "$DOCKER_LOG"
}

@test "the gate keeps the runner's concurrency whatever the container's CPU quota" {
  run "$script" stand
  grep -- '--network none' "$DOCKER_LOG" | grep -q -- '--max-workers=4 -PstandParallelism=4 -Pe2eForks=2 -PpitestThreads=2'
}

@test "a host with less memory than the runner caps the container below the host, warned, not refused" {
  FAKE_MEM=6442450944 run "$script" test
  [ "$status" -eq 0 ]
  [[ "$output" == *"Docker has 6 GB, a runner 16 GB; the suite is capped at 5 GB"* ]]
  grep -q -- '--memory 5g --memory-swap 5g' "$DOCKER_LOG"
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

@test "a run removes the Gradle home it made" {
  run "$script" test
  grep -qE 'volume rm -f jagt-gradle-run-[0-9]+' "$DOCKER_LOG"
}

@test "the install job keeps its network for the Java runtime feed" {
  run "$script" install
  [ "$(grep -c -- '--network none' "$DOCKER_LOG")" -eq 0 ]
}

# bats test_tags=exit:2
@test "an unknown job is refused before Docker is touched" {
  run "$script" nonsense
  [ "$status" -eq 2 ]
  [ ! -e "$DOCKER_LOG" ]
}
