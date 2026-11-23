#!/bin/sh
# reads: the shell command as text on stdin; arguments: names of environment variables holding a pid to protect
# exits: 0 2
# Blocks (exit 2) a call that stops every Gradle daemon or kills java/gradle by name: parallel runs in other worktrees share them.
# Also blocks kill/pkill/killall aimed at $PPID or at a variable named in an argument.
# A numeric pid or one's own background job stays allowed. Quoted text and heredoc bodies are not the command.
cmd=$(cat)
scan=$cmd
if ! printf '%s' "$cmd" | grep -Eq '(^|[;&|([:space:]])((ba|z)?sh[[:space:]]+-[a-z]*c|ev[a]l)[[:space:]]'; then
  scan=$(printf '%s\n' "$cmd" | awk '
    end != "" { if ($0 == end) end = ""; next }
    match($0, /<<-?[[:space:]]*["\047]?[A-Za-z_]+/) {
      t = substr($0, RSTART, RLENGTH); gsub(/<<-?[[:space:]]*["\047]?/, "", t); end = t; $0 = substr($0, 1, RSTART - 1)
    }
    { print }' | perl -0pe 's/"([^"]*\$[^"]*)"/$1/g; s/"[^"]*"//g; s/\x27[^\x27]*\x27//g')
fi
lead='(^|[;&|("'"'"'][[:space:]]*)((eval|sudo|exec|nohup)[[:space:]]+)?'
if printf '%s' "$scan" | grep -Eq "${lead}([^[:space:]]*/)?(gradlew|gradle)[[:space:]][^;&|]*--stop|${lead}(pkill|killall)[[:space:]][^;&|]*(gradle|java|GradleDaemon)"; then
  echo "Refused: this stops every Gradle daemon or java process on the machine, other runs included. Stop only your own run: kill the pid of its gradlew client; the daemon then cancels that build alone." >&2
  exit 2
fi
names=PPID
for n in "$@"; do names="$names|$n"; done
violation=$(printf '%s\n' "$scan" | awk -v names="$names" '
  function check(seg,   n, t, i, w, c, rest, k) {
    gsub(/^[[:space:]({]+/, "", seg)
    n = split(seg, t, /[[:space:]]+/); i = 1
    while (i <= n) {
      w = t[i]
      if (w ~ /^(eval|sudo|exec|nohup|command|time|then|do|else|env)$/ || w ~ /^[A-Za-z_][A-Za-z0-9_]*=/ || w == "") i++
      else if (w == "timeout") { i++; while (t[i] ~ /^-/) i++; i++ }
      else break
    }
    c = t[i]; sub(/^.*\//, "", c)
    if (c !~ /^(kill|pkill|killall)$/) return
    rest = ""
    for (k = i + 1; k <= n; k++) rest = rest " " t[k]
    if (rest ~ ("(^|[^A-Za-z_])\\$[{]?(" names ")([^A-Za-z0-9_]|$)")) { print "kill"; exit }
  }
  { m = split($0, segs, /[;&|`"\047]/); for (j = 1; j <= m; j++) check(segs[j]) }')
if [ "$violation" = kill ]; then
  echo "Refused: this signals the session that runs it ($names). Stop only a process you started: kill <numeric pid>." >&2
  exit 2
fi
exit 0
