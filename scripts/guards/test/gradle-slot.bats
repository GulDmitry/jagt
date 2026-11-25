script="$BATS_TEST_DIRNAME/../../../orchestrator-backend/scripts/gradle-slot.sh"

setup() {
  dir=$(mktemp -d)
  export GRADLE_SLOT_DIR="$dir" GRADLE_SLOTS=1 GRADLE_SLOT_POLL=0.1
  unset GRADLE_SLOT_HELD
}

teardown() {
  rm -rf "$dir"
}

# bats test_tags=exit:0
@test "a free slot runs the command and returns its exit code" {
  run "$script" sh -c 'exit 7'
  [ "$status" -eq 7 ]
  [[ "$output" == *"slot 1"* ]]
}

@test "the slot is released when the command ends" {
  "$script" true
  [ ! -d "$dir/1" ]
}

@test "the command sees the slot it holds" {
  run "$script" sh -c 'echo held=$GRADLE_SLOT_HELD'
  [[ "$output" == *"held=1"* ]]
}

@test "a call nested under a held slot runs at once without taking another" {
  GRADLE_SLOT_HELD=1 run "$script" echo nested
  [ "$status" -eq 0 ]
  [ "$output" = "nested" ]
  [ ! -d "$dir/1" ]
}

# bats test_tags=exit:75
@test "a full cap makes the next call wait, then exit 75 when none frees" {
  mkdir "$dir/1"
  echo $$ > "$dir/1/pid"
  GRADLE_SLOT_WAIT=1 run "$script" true
  [ "$status" -eq 75 ]
  [[ "$output" == *"no slot free"* ]]
}

@test "a slot whose holder died is reclaimed" {
  mkdir "$dir/1"
  sh -c 'echo $$' > "$dir/1/pid"
  run "$script" echo reclaimed
  [ "$status" -eq 0 ]
  [[ "$output" == *"reclaimed"* ]]
}

@test "a waiting call starts once the holder ends" {
  "$script" sleep 1 &
  sleep 0.3
  GRADLE_SLOT_WAIT=10 run "$script" echo after
  wait
  [ "$status" -eq 0 ]
  [[ "$output" == *"after"* ]]
}

@test "the cap is the number of slots configured" {
  export GRADLE_SLOTS=2
  mkdir "$dir/1"
  echo $$ > "$dir/1/pid"
  run "$script" true
  [[ "$output" == *"slot 2"* ]]
}

@test "a second call from the same tree waits while the first holds a slot" {
  export GRADLE_SLOTS=2
  mkdir "$dir/1"
  echo $$ > "$dir/1/pid"
  pwd -P > "$dir/1/tree"
  GRADLE_SLOT_WAIT=1 run "$script" true
  [ "$status" -eq 75 ]
}

@test "a call from another tree takes a free slot beside a held one" {
  export GRADLE_SLOTS=2
  mkdir "$dir/1"
  echo $$ > "$dir/1/pid"
  echo /elsewhere > "$dir/1/tree"
  run "$script" true
  [ "$status" -eq 0 ]
  [[ "$output" == *"slot 2"* ]]
}
