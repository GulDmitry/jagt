## Why

Acceptance that only runs flows it is given is a regular session with fewer tools: every check re-derives how to sign in,
where a page is and what a criterion means. A workspace of its own lets it write a scenario once and reuse it, so later
checks read less and spend fewer tokens.

## What Changes

- `roles/acceptance/role.yml` names `workspace`: a path, `roles/acceptance/workspace/` (gitignored) by default, `none`
  for today's memoryless read.
- With a workspace, acceptance reads its `index.md` first, reuses a scenario or step, else decomposes the criterion into
  WHEN/THEN scenarios and steps, writes the flow and runs it headless; what it learnt goes back into the index.
- Write and Edit reach the workspace alone; its one extra shell form is the role's flows command, run there.
- A flow it writes may click and create data on a listed environment alone; no push, no base branch, no credential in its
  context.
- jagt never commits the workspace.
- `setup.md.dist` asks the technology the project has, the workspace path and its layout.

## Impact

- Specs: `acceptance`, and the new `acceptance-workspace`, which holds the tools, workspace and ticket-analysis requirements.
- `RoleConfig`, the read gate's role tools, `AcceptanceRunner`, `roles/acceptance/role.yml.dist`, `brief.md.dist`,
  `setup.md.dist`, the acceptance eval.
- Decision 0158 (reopens 0157's read-only frame for the workspace alone).
