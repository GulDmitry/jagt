#!/bin/bash
worktree="$1"
cd "$worktree" || exit

post() {
  exec 3<>/dev/tcp/127.0.0.1/8290
  printf 'POST %s HTTP/1.0\r\nHost: 127.0.0.1:8290\r\nContent-Type: application/json\r\nX-Working-Directory: %s\r\nContent-Length: %s\r\n\r\n%s' \
    "$1" "$worktree" "${#2}" "$2" >&3
  cat <&3 >/dev/null
}

post /api/agent/session/working ''
if grep -q "keep working" task_request.md; then
  echo "stub working"
else
  printf 'widget fixed\n' > task_notes.md
  echo changed >> a.txt
  git -c user.name=stub -c user.email=stub@localhost commit -qam "stub change"
  post /mcp '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"update_agent_status","arguments":{"status":"REVIEW_PENDING","message":"done","why":"the stub reports"}}}'
  echo "stub ready"
fi
while read -r line; do echo "got: $line"; done
