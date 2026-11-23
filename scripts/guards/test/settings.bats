settings="$BATS_TEST_DIRNAME/../../../.claude/settings.json"

hook_for() {
  jq -r --arg g "$1" '.hooks.PreToolUse[].hooks[].command | select(contains($g))' "$settings"
}

run_hook() {
  jq -n --arg a "$2" --arg c "$3" '{tool_name:"Bash",agent_type:$a,tool_input:{command:$c}}' | sh -c "$(hook_for "$1")"
}

@test "every guard has exactly one settings entry" {
  for g in no-poll-loop no-shared-kill push-after-local-ci reviewer-read-only; do
    [ "$(hook_for "$g" | wc -l)" -eq 1 ]
  done
}

@test "the poll guard refuses a polling loop from the payload's command" {
  cd "$BATS_TEST_DIRNAME"
  run run_hook no-poll-loop "" "while true; do sleep 1; done"
  [ "$status" -eq 2 ]
}

@test "the read-only guard refuses a write by the reviewer agent" {
  cd "$BATS_TEST_DIRNAME"
  run run_hook reviewer-read-only jagt-reviewer "rm -rf build"
  [ "$status" -eq 2 ]
}

@test "the read-only guard passes a write by another agent" {
  cd "$BATS_TEST_DIRNAME"
  run run_hook reviewer-read-only jagt-implementer "rm -rf build"
  [ "$status" -eq 0 ]
}
