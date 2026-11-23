#!/bin/sh
# Usage: ci-local.sh [gradle-task...]   (from the checkout; default: linux-suite.sh's own four suites)
# Runs CI's suites on a real Linux (linux-suite.sh, in a container) over the tree as it stands, and on exit 0
# records that tree's hash in the git dir; guards/push-after-local-ci.sh lets a push through only when HEAD's tree is the recorded one.
# JAGT_CI_LOCAL_SUITE replaces the suite command.
case "${1:-}" in -h|--help) sed -n '2,6p' "$0" | cut -c3-; exit 0 ;; -*) echo "ci-local: arguments are Gradle tasks, not options: $1" >&2; exit 2 ;; esac
root=$(git rev-parse --show-toplevel) || exit 1
cd "$root" || exit 1
untracked=$(git ls-files --others --exclude-standard -- orchestrator-backend .github)
if [ -n "$untracked" ]; then
  echo "ci-local: untracked files would be tested but not recorded; add or remove them first:" >&2
  echo "$untracked" >&2
  exit 1
fi
wip=$(git stash create)
tree=$(git rev-parse "${wip:-HEAD}^{tree}") || exit 1
suite=${JAGT_CI_LOCAL_SUITE:-"$root/orchestrator-backend/scripts/linux-suite.sh"}
# shellcheck disable=SC2086 # the suite is a command line
$suite "$@"
rc=$?
if [ "$rc" -eq 0 ]; then
  printf '%s\n' "$tree" > "$(git rev-parse --git-common-dir)/ci-local.ok"
  echo "ci-local: green, tree $tree recorded"
else
  echo "ci-local: red (exit $rc), nothing recorded" >&2
fi
exit "$rc"
