# 0158 — Acceptance keeps its checks in a workspace

[← decisions](README.md) · rule: [`acceptance`](../../openspec/specs/acceptance/spec.md) · reopens
[0157](0157-a-roles-options-live-in-its-folder.md)'s read-only read for the workspace alone

## Status

Accepted 2026-11-09 by the owner.

## Context

- A read-only acceptance re-derives every check: how a role signs in, where a page is, what a criterion means.
- A regular session could do the same; acceptance earns its role only by organising its checks.

## Decision

- **`workspace` in `role.yml`**: a path, `roles/acceptance/workspace/` (gitignored) by default; `none` keeps the
  memoryless read.
- **Spec-like layout**: `index.md` read first (environments, roles and how each signs in, scenarios known),
  `scenarios/` as WHEN/THEN, `steps/` reusable, `flows/` runnable; the user's own layout where `setup.md` wrote one.
- **Write and Edit under the workspace alone**, by the read gate's path globs; the one extra shell form is the flows
  command, cwd the workspace, `BASE_URL` a listed environment.
- **A flow may click and create data** on a listed environment; never a push, the base branch or a credential in context.
- **jagt never commits the workspace**: the user commits it where it is a repository of theirs.
- Every role's brief opens with its motivation in one line.

## Rejected

- Write anywhere in the role's folder: the gate's globs would reach `role.yml` and `brief.md`.

## Reopen when

- A flow acceptance wrote reaches a host its environments do not list: the gate sees the command's `BASE_URL`, not the
  hosts inside the flow.
