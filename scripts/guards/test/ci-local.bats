script="$BATS_TEST_DIRNAME/../../../orchestrator-backend/scripts/ci-local.sh"

setup() {
  repo=$(mktemp -d)
  git -C "$repo" init -q -b release
  echo a > "$repo/f"
  git -C "$repo" add f
  git -C "$repo" -c user.name=t -c user.email=t@t commit -q -m one
  cd "$repo" || return 1
}

teardown() {
  rm -rf "$repo"
}

# bats test_tags=exit:0
@test "a green suite records the tree it ran on, uncommitted changes included" {
  echo b > f
  JAGT_CI_LOCAL_SUITE=true run "$script"
  [ "$status" -eq 0 ]
  git add f && git -c user.name=t -c user.email=t@t commit -q -m two
  [ "$(cat .git/ci-local.ok)" = "$(git rev-parse 'HEAD^{tree}')" ]
}

@test "a green run of a chosen job records nothing" {
  JAGT_CI_LOCAL_SUITE=true run "$script" heavy
  [ "$status" -eq 0 ]
  [ ! -f .git/ci-local.ok ]
}

# bats test_tags=exit:1
@test "a red suite records nothing" {
  JAGT_CI_LOCAL_SUITE=false run "$script"
  [ "$status" -eq 1 ]
  [ ! -f .git/ci-local.ok ]
}

@test "an untracked file under the build is refused before any run" {
  mkdir -p orchestrator-backend && echo x > orchestrator-backend/New.java
  JAGT_CI_LOCAL_SUITE=true run "$script"
  [ "$status" -eq 1 ]
  [ ! -f .git/ci-local.ok ]
}

# bats test_tags=exit:2
@test "an option is refused before any run, so it never records a green" {
  JAGT_CI_LOCAL_SUITE=true run "$script" --info
  [ "$status" -eq 2 ]
  [ ! -f .git/ci-local.ok ]
}

# bats test_tags=opt:-h
@test "help prints the usage and runs nothing" {
  JAGT_CI_LOCAL_SUITE=false run "$script" --help
  [ "$status" -eq 0 ]
  [[ "$output" == *"Usage: ci-local.sh"* ]]
  [ ! -f .git/ci-local.ok ]
}
