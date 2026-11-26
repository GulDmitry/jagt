#!/bin/sh
# Fails on every finding of severity error in the SARIF files given, naming its rule and file: the pull request's CodeQL gate
# (.github/workflows/codeql.yml) and its local twin (codeql-local.sh) both end here.
# Usage: codeql-errors.sh <sarif...>
# exits: 0 1 2
set -eu
[ "$#" -gt 0 ] || { echo "codeql-errors: no SARIF file given" >&2; exit 2; }
errors=$(jq -r -s '.[] | .runs[]
  | ([.tool.driver.rules[]?] + [.tool.extensions[]?.rules[]?]) as $rules
  | .results[] | (.ruleId // .rule.id) as $id
  | select((.level // ([$rules[] | select(.id == $id)][0].defaultConfiguration.level) // "warning") == "error")
  | "\($id) \(.locations[0].physicalLocation.artifactLocation.uri // "unknown")"' "$@")
count=0
[ -z "$errors" ] || count=$(printf '%s\n' "$errors" | wc -l | tr -d ' ')
[ "$count" -eq 0 ] || printf '%s\n' "$errors" | sed 's/^/codeql.error /' >&2
echo "codeql.errors=$count"
[ "$count" -eq 0 ]
