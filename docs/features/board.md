# Board tabs
[← Features](../features.md) · Each line names one thing the open task's tabs do and links to the requirement that defines it.

## Board workspace · [spec](../../openspec/specs/board-workspace/spec.md)
- **Live terminal** — The Session tab streams the session's terminal and takes typing from every browser; the window follows its size while open.
  [spec](../../openspec/specs/session-stream/spec.md)
- **Session, Artifacts, Code and Diff tabs** — Watch the session and answer it, read a task's plan and notes, its worktree and its diff.
  [spec](../../openspec/specs/board-workspace/spec.md#requirement-the-open-task-has-its-six-tabs) ·
  [diff](../../openspec/specs/board-diff/spec.md) · [verbs](../../openspec/specs/board-verbs/spec.md)
- **Run a shell line** — The Session tab runs a shell line behind the CLI's escape, in a warm or a cold session; a CLI with none offers no run.
  [spec](../../openspec/specs/board-verbs/spec.md#requirement-a-shell-line-is-typed-behind-the-clis-escape)
- **Plan changes since approval** — The Artifacts tab marks plan lines new or rewritten since your last approval, and strikes removed ones.
  [spec](../../openspec/specs/board/spec.md#requirement-a-plan-shows-what-changed-since-its-last-approval)
