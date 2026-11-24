#!/bin/sh
out=$(mktemp)
trap 'rm -f "$out"' EXIT
code=$({ for a in "$@"; do printf '%s\0' "$a"; done; } | curl -sS -o "$out" -w '%{http_code}' --data-binary @- "@HOST@/gh")
if [ "$code" = 200 ]; then cat "$out"; else cat "$out" >&2; exit 1; fi
