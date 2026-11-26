hook="$BATS_TEST_DIRNAME/../reviewer-read-only.sh"

run_cmd() {
  printf '%s' "$1" | "$hook"
}

denies() {
  run run_cmd "$1"
  [ "$status" -eq 2 ]
  [[ "$output" == Refused:* ]]
}

passes() {
  run run_cmd "$1"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

for cmd in \
  "git commit -m x" \
  "cd x; git push origin main" \
  "git reset --hard HEAD~1" \
  "git checkout main" \
  "rm -rf build" \
  "mv a b" \
  "echo x > file.txt" \
  "sed -i '' s/a/b/ f" \
  "echo x | tee f" \
  "git switch main" \
  "git restore f" \
  "git clean -fd" \
  "git merge main" \
  "git rebase main" \
  "git cherry-pick abc123" \
  "git revert abc123" \
  "git stash" \
  "git add f" \
  "git rm f" \
  "git mv a b" \
  "git tag v1" \
  "git branch -D x" \
  "git branch -d x" \
  "git worktree add ../w" \
  "git worktree remove ../w" \
  "git -C /repo commit -m x" \
  "git -c user.name=t commit -m x" \
  "git log | tee out.txt" \
  "cd x && rm f" \
  "echo x >> f" \
  "sed -n -i p f" \
  "sed -ni p f" \
  "cp a b" \
  "touch x" \
  "mkdir d" \
  "ln -s a b" \
  "chmod +x f" \
  "git apply p.diff" \
  "perl -pi -e 's/a/b/' f" \
  "curl -o f https://example.test" \
  "curl -sSLo f https://example.test" \
  "echo '>' x"; do
  bats_test_function --description "reviewer-read-only denies: $cmd" -- denies "$cmd"
done

for cmd in \
  "git log main..HEAD --oneline && git diff main...HEAD | grep -n foo 2>&1 >/dev/null" \
  "git status --short" \
  "git stash list" \
  "git tag --list" \
  "git branch --list" \
  "sed -n 1,5p f" \
  "grep -rn foo src"; do
  bats_test_function --description "reviewer-read-only passes: $cmd" -- passes "$cmd"
done

# bats test_tags=exit:0
@test "empty input passes" {
  run bash -c "echo -n '' | $hook"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

# bats test_tags=exit:2
@test "a commit is denied" {
  denies "git commit -m x"
}
