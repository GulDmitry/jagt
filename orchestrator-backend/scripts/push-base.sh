#!/usr/bin/env bash
# Prints the commit a push or request started from, for `-PpitestDiff` (0129): the one given, or HEAD~1 where the
# clone does not hold it — a new branch's all-zero sha, a force-push's lost tip, nothing given.
base="${1:-}"
if git cat-file -e "${base:-none}^{commit}" 2>/dev/null; then
    echo "$base"
else
    echo HEAD~1
fi
