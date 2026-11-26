script="$BATS_TEST_DIRNAME/../../../orchestrator-backend/scripts/codeql-errors.sh"

sarif() {
  cat > "$BATS_TEST_TMPDIR/$1.sarif" <<EOT
{"runs":[{"tool":{"driver":{"rules":[{"id":"java/redos","defaultConfiguration":{"level":"$2"}}]}},
"results":[{"ruleId":"java/redos","locations":[{"physicalLocation":{"artifactLocation":{"uri":"src/Pattern.java"}}}]}]}]}
EOT
}

# bats test_tags=exit:1
@test "a finding rated error fails naming its rule and file" {
  sarif red error
  run "$script" "$BATS_TEST_TMPDIR/red.sarif"
  [ "$status" -eq 1 ]
  [[ "$output" == *"codeql.error java/redos src/Pattern.java"* ]]
  [[ "$output" == *"codeql.errors=1"* ]]
}

# bats test_tags=exit:0
@test "a finding rated warning passes" {
  sarif green warning
  run "$script" "$BATS_TEST_TMPDIR/green.sarif"
  [ "$status" -eq 0 ]
  [[ "$output" == *"codeql.errors=0"* ]]
}

# bats test_tags=exit:2
@test "no file given is refused" {
  run "$script"
  [ "$status" -eq 2 ]
}
