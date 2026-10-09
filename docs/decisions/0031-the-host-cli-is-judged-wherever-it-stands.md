# 0031 — The host CLI is judged wherever it stands

[← decisions](README.md) · rule: [`git`](../../openspec/specs/git/spec.md)

## Status

Accepted 2026-10-09. Narrows 0028's rejection of "a shell parser in `ToolGate`" to the host CLI; its other rows,
and 0029's and 0030's, stand.

## Context

- Judging `gh`/`glab` only in command position let `time`, `nohup`, `sudo`, `if`, `{ … }`, `find -exec`,
  `eval` and `bash -c '…'` run `gh pr merge`, which writes the base branch with no pre-push hook.
- Refusing the bare word anywhere refused `grep -rn glab src` and a commit message naming `gh`.

## Decision

- `HostCliLine` reads a line as words, a quoted span one word. A `gh`/`glab` word is a call in command position
  or wherever the next word is one of its commands (every top-level `gh` and `glab` command), a repository flag
  (`-R`, `--repo`, `--hostname`, glued or not) or a brace; a call runs only as a read, and `api` takes only its read
  options.
- After a shell (the sh family, `csh`, `tcsh`, `fish`), `eval` or `env`, every later word is judged again as a line,
  a leading `-S` or `--split-string=` dropped; `ToolGate` also judges the unsplit line, so a separator inside a
  quoted `-c` argument does not hide what follows it.
- A host CLI quoted as data runs; named unquoted before a command group it is refused, the safe direction.

## Rejected

- A full shell grammar: a guardrail with a pre-push hook behind it, not a boundary (0022).

## Reopen when

- A host-CLI write is seen run past `ToolGate` by a spelling these rules read, or the refusals block a read a
  session needs and no other spelling exists.
- Still passing, and named here: a line piped into a shell (`echo '…' | sh`), a here-string (`bash <<< '…'`),
  a language runtime (`python3 -c`), `${IFS}` or any line assembled at runtime (0028).
