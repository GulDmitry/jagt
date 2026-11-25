hook="$BATS_TEST_DIRNAME/../push-after-local-ci.sh"

setup() {
  repo=$(mktemp -d)
  git -C "$repo" init -q -b release
  git -C "$repo" -c user.name=t -c user.email=t@t commit -q --allow-empty -m one
  cd "$repo" || return 1
}

teardown() {
  rm -rf "$repo"
}

run_cmd() {
  printf '%s' "$1" | "$hook"
}

# bats test_tags=exit:2
@test "a push from release without a local green is refused" {
  run run_cmd "git push origin release"
  [ "$status" -eq 2 ]
  [[ "$output" == Refused:* ]]
}

# bats test_tags=exit:0
@test "a push from release whose tree ran green passes" {
  git rev-parse 'HEAD^{tree}' > .git/ci-local.ok
  run run_cmd "git push"
  [ "$status" -eq 0 ]
}

@test "a push after a commit the local run did not see is refused" {
  git rev-parse 'HEAD^{tree}' > .git/ci-local.ok
  echo x > f && git add f && git -c user.name=t -c user.email=t@t commit -q -m two
  run run_cmd "git push origin release"
  [ "$status" -eq 2 ]
}

@test "a task branch push passes" {
  git checkout -q -b task/abc-42
  run run_cmd "git push -u origin task/abc-42"
  [ "$status" -eq 0 ]
}

@test "a commit message naming git push passes" {
  run run_cmd 'git commit -m "git push release"'
  [ "$status" -eq 0 ]
}

@test "a multi-line quoted argument naming git push passes" {
  run run_cmd $'backlog task edit ABC-42 --notes "State: x\nNext: run the gate, exit 0; git push origin release\nDecided: none"'
  [ "$status" -eq 0 ]
}

@test "a push from a hotfix branch without a local green is refused" {
  git checkout -q -b hotfix/abc-42
  run run_cmd "git push -u origin hotfix/abc-42"
  [ "$status" -eq 2 ]
  [[ "$output" == Refused:* ]]
}

@test "a push from a hotfix branch whose tree ran green passes" {
  git checkout -q -b hotfix/abc-42
  git rev-parse 'HEAD^{tree}' > .git/ci-local.ok
  run run_cmd "git push -u origin hotfix/abc-42"
  [ "$status" -eq 0 ]
}

@test "a push written git -C path push from release without a local green is refused" {
  run run_cmd "git -C /somewhere push origin release"
  [ "$status" -eq 2 ]
}

@test "a push of HEAD:release from a task branch without a local green is refused" {
  git checkout -q -b task/abc-42
  run run_cmd "git push origin HEAD:release"
  [ "$status" -eq 2 ]
}

@test "a push after a semicolon from release without a local green is refused" {
  run run_cmd "cd x; git push"
  [ "$status" -eq 2 ]
}

@test "a push whose release is only inside quotes from a task branch passes" {
  git checkout -q -b task/abc-42
  run run_cmd 'git push origin task/abc-42 --push-option="release"'
  [ "$status" -eq 0 ]
}

@test "a worktree push reads the green marker of the shared git directory" {
  git worktree add -q "$repo-wt" -b hotfix/abc-43
  git rev-parse 'HEAD^{tree}' > .git/ci-local.ok
  cd "$repo-wt"
  run run_cmd "git push origin hotfix/abc-43"
  cd "$repo"
  git worktree remove --force "$repo-wt"
  [ "$status" -eq 0 ]
}

@test "a push naming an older commit whose tree ran green passes, though HEAD moved on" {
  git rev-parse 'HEAD^{tree}' > .git/ci-local.ok
  green=$(git rev-parse HEAD)
  echo x > f && git add f && git -c user.name=t -c user.email=t@t commit -q -m two
  run run_cmd "git push origin $green:release"
  [ "$status" -eq 0 ]
}

@test "a push naming a commit the local run did not see is refused, though HEAD ran green" {
  echo x > f && git add f && git -c user.name=t -c user.email=t@t commit -q -m two
  git rev-parse 'HEAD^{tree}' > .git/ci-local.ok
  run run_cmd "git push origin HEAD~1:release"
  [ "$status" -eq 2 ]
}

@test "a push from another worktree checks that worktree's tree" {
  git rev-parse 'HEAD^{tree}' > .git/ci-local.ok
  git worktree add -q --detach "$repo/wt" HEAD
  echo x > f && git add f && git -c user.name=t -c user.email=t@t commit -q -m two
  run run_cmd "git -C $repo/wt push origin HEAD:release"
  [ "$status" -eq 0 ]
}
