#!/bin/sh
# reads: the shell command as text on stdin
# exits: 0 2
# Blocks (exit 2) a write: git commit/push/reset/checkout/..., rm, mv, a > redirect, sed -i, tee. For a session that only reads.
cmd=$(cat)
scan=$(printf '%s' "$cmd" | sed -E 's#[0-9]*>&[0-9-]#  #g; s#[0-9]*>[[:space:]]*/dev/null# #g')
if printf '%s' "$scan" | grep -Eq '(^|[;&|(`[:space:]])(git[[:space:]]+(-[^[:space:]]+[[:space:]]+([^-[:space:]][^[:space:]]*[[:space:]]+)?)*(commit|push|reset|checkout|switch|restore|clean|merge|rebase|cherry-pick|revert|stash[[:space:]]*($|[;&|)])|stash[[:space:]]+(push|pop|drop|apply|clear|save|branch|create|store)|add|rm|mv|tag[[:space:]]+(-[dafs]|[^-[:space:]][^[:space:]]*)|branch[[:space:]]+-[dD]|worktree[[:space:]]+(add|remove))|rm|mv|tee|sed[[:space:]]+(-[^[:space:]]*[[:space:]]+)*-[a-zA-Z]*i)([[:space:]]|$)|>'; then
  echo "Refused: this session is read-only: no commit, push, reset, checkout, rm, mv, redirect, sed -i or tee. Report the finding instead." >&2
  exit 2
fi
exit 0
