#!/bin/bash
worktree=$(pwd -P)
slug=$(printf '%s' "$worktree" | sed 's/[^A-Za-z0-9-]/-/g')
dir="${CLAUDE_CONFIG_DIR:-$HOME/.claude}/projects/$slug"
mkdir -p "$dir"
log="$dir/conversation.jsonl"

post() {
  exec 3<>/dev/tcp/127.0.0.1/@PORT@
  printf 'POST %s HTTP/1.0\r\nHost: 127.0.0.1:@PORT@\r\nContent-Type: application/json\r\nX-Working-Directory: %s\r\nContent-Length: %s\r\n\r\n%s' \
    "$1" "$worktree" "${#2}" "$2" >&3
  cat <&3 >/dev/null
}

if printf '%s\n' "$@" | grep -qx -- '--continue'; then
  echo "resumed: $(wc -l <"$log" | tr -d ' ') lines"
  cat "$log"
else
  : >"$log"
  post /api/agent/session/working ''
  if grep -q "keep working" task_request.md; then
    echo "agent working"
  else
    printf 'widget fixed\n' >task_notes.md
    echo changed >>a.txt
    git -c user.name=agent -c user.email=agent@localhost commit -qam "agent change"
    post /mcp '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"update_agent_status","arguments":{"status":"REVIEW_PENDING","message":"done","why":"the agent reports"}}}'
    echo "agent ready"
  fi
fi
while read -r line; do
  printf '%s\n' "$line" >>"$log"
  echo "got: $line"
done
