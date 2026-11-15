# Task flow
[← Features](../features.md) · Each line names one thing a task's life does and links to the requirement that defines it.

## Task flow · [spec](../../openspec/specs/task-flow/spec.md)
- **One table of statuses** — One table says which action is allowed in which status and where it leads.
  [spec](../../openspec/specs/task-flow/spec.md#requirement-one-table-owns-the-life-of-a-task)
- **Verify before hand-back** — jagt runs the project's check command and sends a red run back to the session.
  [spec](../../openspec/specs/task-flow/spec.md#requirement-a-hand-back-waits-at-verifying)
- **Deploy check** — A deploy is checked where it landed — by its session, or by an acceptance run when `roles.acceptance.enabled` (off).
  A green pipeline stands it, a red one is re-run once, then goes back to the session in the deploy worktree. Key: `codeHost`.
  [spec](../../openspec/specs/deploy-check/spec.md#requirement-every-deploy-is-checked-where-it-landed)
  · [role](../../openspec/specs/acceptance/spec.md)
  · [host](../../openspec/specs/deploy-check/spec.md#requirement-the-code-host-is-chosen)
- **Acceptance workspace** — Acceptance keeps scenarios in its own folder, writes only there, and asks one question per ticket gap in a session.
  [spec](../../openspec/specs/acceptance-workspace/spec.md#requirement-acceptance-keeps-its-checks-in-its-workspace)
  [spec](../../openspec/specs/acceptance-workspace/spec.md#requirement-acceptance-analyses-a-ticket-before-its-plan)
  [spec](../../openspec/specs/acceptance-workspace/spec.md#requirement-acceptance-has-an-interactive-session)
- **Stalled conflict** — A deploy conflict handed to its session that stops moving is yours: one banner, the card says why.
  [spec](../../openspec/specs/task-flow/spec.md#requirement-a-stalled-deploy-conflict-is-the-humans)
- **Starting a task** — A task starts from a ticket or a sentence, in its own worktree on its own branch.
  [spec](../../openspec/specs/task-start/spec.md#requirement-starting-a-task)
- **Plan first** — A task asked to plan, or under autoplan, changes no file but its plan until you approve the plan on the board.
  [spec](../../openspec/specs/task-start/spec.md#requirement-plan-first-is-a-gate-jagt-holds)
- **Plan before a project** — `plan first`, no project ticked: plans in a scratch folder; approval cuts each `[changed]` project's worktree.
  [spec](../../openspec/specs/task-start/spec.md#requirement-a-task-may-plan-before-it-has-a-repository)
