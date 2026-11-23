hook="$BATS_TEST_DIRNAME/../no-shared-kill.sh"

run_cmd() {
  printf '%s' "$1" | "$hook" "${@:2}"
}

denies() {
  run run_cmd "$1" "${@:2}"
  [ "$status" -eq 2 ]
  [[ "$output" == Refused:* ]]
}

passes() {
  run run_cmd "$1" "${@:2}"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

for cmd in \
  "gradle --stop" \
  "./gradlew --stop" \
  "cd orchestrator-backend && ./gradlew --stop" \
  "/opt/gradle/bin/gradle build --stop" \
  "killall java" \
  "killall -9 GradleDaemon" \
  "pkill -f gradle" \
  "cd x; pkill -9 java" \
  "echo go | pkill GradleDaemon" \
  "sh -c 'killall gradle'" \
  'bash -c "pkill java"' \
  "eval pkill java" \
  "kill \$PPID" \
  "kill -9 \${PPID}"; do
  bats_test_function --description "no-shared-kill denies: ${cmd//[\$\`]/#}" -- denies "$cmd"
done

for cmd in \
  "kill 4242" \
  "kill -9 4242" \
  "pkill -f my-own-script" \
  "killall node" \
  "./gradlew build --no-daemon" \
  "./gradlew test --tests FooTest" \
  "cat gradle.properties" \
  "kill \$!" \
  "kill %1" \
  "kill \$FAKE_PPID" \
  "echo \"kill \$PPID\"" \
  "grep -rn 'gradlew --stop' docs" \
  "echo 'run ./gradlew --stop'" \
  "git log --grep='pkill java'" \
  "git commit -m 'pkill java kills every build'"; do
  bats_test_function --description "no-shared-kill passes: ${cmd//[\$\`]/#}" -- passes "$cmd"
done

# bats test_tags=exit:2
@test "a kill of a variable named as an argument is denied" {
  denies 'kill $SESSION_PID' SESSION_PID
  denies 'kill -TERM "$SESSION_PID"' SESSION_PID
  denies 'kill ${OTHER_PID}' SESSION_PID OTHER_PID
}

# bats test_tags=exit:0
@test "a kill of a variable not named as an argument passes" {
  passes 'kill $SESSION_PID'
  passes 'kill $SESSION_PID_X' SESSION_PID
}

# bats test_tags=exit:0
@test "empty input passes" {
  run bash -c "echo -n '' | $hook"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "a heredoc body naming killall gradle passes" {
  run run_cmd $'git commit -F - <<\'EOF\'\nkillall gradle is refused\nEOF'
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}
