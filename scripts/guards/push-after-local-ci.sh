#!/bin/sh
# reads: the shell command as text on stdin
# exits: 0 2
# Blocks (exit 2) a git push from release or a hotfix branch whose pushed tree (the refspec's source, else HEAD; -C honoured) orchestrator-backend/scripts/ci-local.sh has not
# run green: each push re-runs every CI job and each red costs a round.
cmd=$(cat)
scan=$(printf '%s\n' "$cmd" | sed -E "s/\"[^\"]*\"//g; s/'[^']*'//g")
printf '%s' "$scan" | grep -Eq '(^|[;&|(][[:space:]]*)git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+push([[:space:]]|$)' || exit 0
branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
case "$branch $scan" in
  release* | hotfix/* | *" release"* | *:release* | *hotfix/*) ;;
  *) exit 0 ;;
esac
dir=$(printf '%s' "$scan" | sed -nE 's/.*git[[:space:]]+-C[[:space:]]+([^[:space:]]+)[[:space:]]+push.*/\1/p')
src=$(printf '%s' "$scan" | sed -nE 's/.*[[:space:]]\+?([^[:space:]:+]+):(refs\/heads\/)?(release|hotfix\/[^[:space:]]+)([[:space:]].*)?$/\1/p')
ok=$(cat "$(git -C "${dir:-.}" rev-parse --git-common-dir)/ci-local.ok" 2>/dev/null)
tree=$(git -C "${dir:-.}" rev-parse "${src:-HEAD}^{tree}" 2>/dev/null)
[ "$ok" = "$tree" ] && exit 0
echo "Refused: HEAD's tree has no local CI green. Fix every red job of the last run, run orchestrator-backend/scripts/ci-local.sh (in the background), commit exactly what it tested, then push once." >&2
exit 2
