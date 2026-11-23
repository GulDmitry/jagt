hook="$BATS_TEST_DIRNAME/../no-poll-loop.sh"

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
  "until [ -f done.flag ]; do sleep 5; done" \
  "while ! grep -q ok build.log; do sleep 2; done" \
  "while true; do sleep 60; done" \
  "while kill -0 4242 2>/dev/null; do sleep 1; done" \
  "for i in 1 2 3; do ./probe && break; sleep 10; done" \
  "cd x && until test -s out.txt; do sleep 1; done; cat out.txt" \
  "watch -n 5 ls" \
  "cd x; watch cat log" \
  "sudo watch df" \
  "/usr/bin/watch ls" \
  "sh -c 'until [ -f x ]; do sleep 1; done'" \
  "bash -c \"while true; do sleep 1; done\""; do
  bats_test_function --description "no-poll-loop denies: ${cmd//[;\$\`]/,}" -- denies "$cmd"
done

for cmd in \
  "sleep 5" \
  "./run.sh && sleep 2 && ./check.sh" \
  "while read -r l; do echo \"\$l\"; done < in.txt" \
  "for f in a b; do cat \$f; done" \
  "until [ -f x ]; do ./retry; done" \
  "while read -r l; do echo \$l; done < in.txt; sleep 1" \
  "git commit -m 'replace the until loop with sleep by a notification'" \
  "git commit -m \"do not run watch or while true; do sleep\"" \
  "echo 'while true; do sleep 1; done'" \
  "grep -rn 'until .*sleep' docs" \
  "git log --grep=watch" \
  "gh run watch --exit-status" \
  "echo watch me" \
  "cat stopwatch.log"; do
  bats_test_function --description "no-poll-loop passes: ${cmd//[;\$\`]/,}" -- passes "$cmd"
done

# bats test_tags=exit:0
@test "empty input passes" {
  run bash -c "echo -n '' | $hook"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

# bats test_tags=exit:2
@test "a multi-line until loop with sleep on its own line is denied" {
  run run_cmd $'until [ -f done.flag ]\ndo\n  sleep 5\ndone'
  [ "$status" -eq 2 ]
  [[ "$output" == Refused:* ]]
}

@test "a multi-line while loop with sleep after a command is denied" {
  run run_cmd $'while true; do\n  ./check\n  sleep 3\ndone'
  [ "$status" -eq 2 ]
  [[ "$output" == Refused:* ]]
}

@test "a heredoc body naming a poll loop and watch passes" {
  run run_cmd $'git commit -F - <<\'EOF\'\nuntil x; do sleep 1; done\nwatch ls\nEOF'
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}
