#!/bin/sh
# reads: the shell command as text on stdin
# exits: 0 2
# Blocks (exit 2) a write: git commit/push/reset/checkout/apply/..., rm, mv, cp, touch, mkdir, ln, chmod, a > redirect, sed -i, perl -i, tee, curl -o. For a session that only reads.
cmd=$(cat)
scan=$(printf '%s' "$cmd" | sed -E 's#[0-9]*>&[0-9-]#  #g; s#[0-9]*>[[:space:]]*/dev/null# #g')
if printf '%s' "$scan" | grep -Eq '(^|[;&|(`[:space:]])(git[[:space:]]+(-[^[:space:]]+[[:space:]]+([^-[:space:]][^[:space:]]*[[:space:]]+)?)*(commit|push|reset|checkout|switch|restore|clean|merge|rebase|cherry-pick|revert|stash[[:space:]]*($|[;&|)])|stash[[:space:]]+(push|pop|drop|apply|clear|save|branch|create|store)|add|apply|rm|mv|tag[[:space:]]+(-[dafs]|[^-[:space:]][^[:space:]]*)|branch[[:space:]]+-[dD]|worktree[[:space:]]+(add|remove))|rm|mv|cp|touch|mkdir|ln|chmod|tee|sed[[:space:]]+(-[^[:space:]]*[[:space:]]+)*-[a-zA-Z]*i|perl[[:space:]]+(-[^[:space:]]*[[:space:]]+)*-[a-zA-Z]*i[^[:space:]]*|curl[[:space:]]+([^;&|]*[[:space:]])?(-[a-zA-Z]*[oO]|--output|--remote-name))([[:space:]]|$)|>'; then
  echo "Refused: this session is read-only: no commit, push, reset, checkout, apply, rm, mv, cp, touch, mkdir, ln, chmod, redirect, sed -i, perl -i, tee or curl -o. Report the finding instead." >&2
  exit 2
fi
exit 0
