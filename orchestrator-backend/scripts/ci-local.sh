#!/bin/sh
# Usage: ci-local.sh [job...]   (jobs: test heavy stand stand-kind install install-macos audit nightly-*; nightly = every nightly-*; default: the PR jobs)
# Runs a pull request's CI jobs over the tree as it stands: each Linux job's gate in a container (linux-suite.sh),
# install-macos's natively. Only the argument-free run, green, records the tree's hash in the git dir;
# ci-local.greens keeps every one, which `./gradlew divergenceRegister` reads against CI's red runs.
# guards/push-after-local-ci.sh lets a push through only when HEAD's tree is the recorded one.
# audit also builds and scans the base images on the host's Docker (`baseImageScan`), base-images.yml's per-image step.
# nightly runs every job of nightly.yml, listed once in linux-suite.sh, and records nothing, as any named job.
# JAGT_CI_LOCAL_SUITE replaces both runners, given every job.
# exits: 0 1 2
# options: -h
case "${1:-}" in -h|--help) sed -n '2,8p' "$0" | cut -c3-; exit 0 ;; -*) echo "ci-local: arguments are jobs, not options: $1" >&2; exit 2 ;; esac
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
jobs=${*:-test heavy stand stand-kind install install-macos audit}
linux=; macos=; images=
nightly=$(sed -n "s/^ *\(nightly-[a-z0-9-]*\)) echo '\.\/gradlew.*/\1/p" "$(dirname "$0")/linux-suite.sh")
expanded=
for each in $jobs; do
  case "$each" in nightly) expanded="$expanded $nightly" ;; *) expanded="$expanded $each" ;; esac
done
jobs=${expanded# }
for each in $jobs; do
  case "$each" in install-macos) macos=1 ;; audit) images=1; linux="$linux $each" ;; *) linux="$linux $each" ;; esac
done
rc=0
if [ -n "${JAGT_CI_LOCAL_SUITE:-}" ]; then
  # shellcheck disable=SC2086 # the suite is a command line and the jobs are words
  $JAGT_CI_LOCAL_SUITE $jobs || rc=$?
else
  # shellcheck disable=SC2086 # the jobs are words
  [ -z "$linux" ] || orchestrator-backend/scripts/linux-suite.sh $linux || rc=$?
  [ -z "$macos" ] || (cd orchestrator-backend && ./gradlew --no-daemon gateInstall) || rc=$?
  [ -z "$images" ] || (cd orchestrator-backend && ./gradlew --no-daemon baseImageScan -Paudit.base=origin/main) || rc=$?
fi
if [ "$rc" -ne 0 ]; then
  echo "ci-local: red (exit $rc), nothing recorded" >&2
elif [ "$#" -eq 0 ]; then
  common=$(git rev-parse --git-common-dir)
  printf '%s\n' "$tree" > "$common/ci-local.ok"
  printf '%s\n' "$tree" >> "$common/ci-local.greens"
  echo "ci-local: green, tree $tree recorded"
else
  echo "ci-local: green on$linux${macos:+ install-macos}; only the argument-free run records a tree"
fi
exit "$rc"
