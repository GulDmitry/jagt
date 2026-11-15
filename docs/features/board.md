# Board tabs
[← Features](../features.md) · Each line names one thing the open task's tabs do and links to the requirement that defines it.

## Board workspace · [spec](../../openspec/specs/board-workspace/spec.md)
- **Session, Artifacts and Diff tabs** — Read the session's screen and answer it; read a task's plan and notes rendered, and its diff per repository.
  [spec](../../openspec/specs/board-workspace/spec.md#requirement-the-open-task-has-its-five-tabs) ·
  [diff](../../openspec/specs/board-diff/spec.md) · [verbs](../../openspec/specs/board-verbs/spec.md)
- **Run a shell line** — The Session tab runs a shell line behind the CLI's escape, in a warm or a cold session; a CLI with none offers no run.
  [spec](../../openspec/specs/board-verbs/spec.md#requirement-a-shell-line-is-typed-behind-the-clis-escape)
- **Plan changes since approval** — The Artifacts tab marks plan lines new or rewritten since your last approval, and strikes removed ones.
  [spec](../../openspec/specs/board/spec.md#requirement-a-plan-shows-what-changed-since-its-last-approval)
