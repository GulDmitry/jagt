#!/bin/sh
# reads: the shell command as text on stdin
# exits: 0 2
# Blocks (exit 2) a Bash call that waits by polling: an until/while/for loop whose body sleeps, or `watch`.
# A background job notifies on exit, so a poll only burns turns. A single sleep, a loop without sleep,
# quoted text and heredoc bodies are not a poll.
cmd=$(cat)
scan=$cmd
if ! printf '%s' "$cmd" | grep -Eq '(^|[;&|([:space:]])((ba|z)?sh[[:space:]]+-[a-z]*c|ev[a]l)[[:space:]]'; then
  scan=$(printf '%s\n' "$cmd" | awk '
    end != "" { if ($0 == end) end = ""; next }
    match($0, /<<-?[[:space:]]*["\047]?[A-Za-z_]+/) {
      t = substr($0, RSTART, RLENGTH); gsub(/<<-?[[:space:]]*["\047]?/, "", t); end = t; $0 = substr($0, 1, RSTART - 1)
    }
    { print }' | perl -0pe 's/"[^"]*"//g; s/\x27[^\x27]*\x27//g')
fi
lead='(^|[;&|(]|\n)[[:space:]]*'
loop=$(printf '%s\n' "$scan" | perl -0ne 'print "loop" if /(?:^|[;&|("\x27\s])(?:until|while|for)\s(?:(?!(?:^|[;&|\s])done(?:[;&|)"\x27\s]|$)).)*?(?:^|[;&|("\x27\s])sleep\s/s')
if [ -n "$loop" ] || printf '%s' "$scan" | grep -Eq "${lead}((sudo|exec|nohup)[[:space:]]+)?([^[:space:]]*/)?watch([[:space:]]|\$)"; then
  echo "Refused: this waits by polling (a loop with sleep, or watch). A background job notifies when it exits: end the turn and its notification wakes you; for a CI run use one gh run watch --exit-status." >&2
  exit 2
fi
exit 0
