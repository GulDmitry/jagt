# 0126 — A role is a folder

[← decisions](README.md) · rule: [`cases`](../../openspec/specs/cases/spec.md) · amends
[0122](0122-a-role-is-a-module-the-chart-composes.md), supersedes [0123](0123-configuration-is-layered-outside-the-project.md)

## Status

Proposed 2026-11-02 by the owner's order; accepted with the commit moving QA's config onto its folder.

## Context

- QA lives in `roles/qa/`; the Master and the session keep brief and cases in the root, config in `jagt.yml`,
  evals as Java in `src/masterEval`. Four loaders (`MasterBriefs`, `MasterCases`, `SessionBrief`, `SessionCases`)
  each hard-code a root path; `RoleFiles` already resolves a role folder but only QA uses it.
- 0123 planned a location map and four layers for every kind of file; the owner narrowed it to roles first.

## Decision

- Everything of a role lives in `roles/<id>/`: `role.yml`, `brief.md`, `cases.md`, `eval/*.yml`, each shipped as
  `.dist` beside a copy you may write. The ids are `master`, `session`, `qa`.
- One loader, `RoleFiles`, resolves every role file; the four fixed-name loaders read through it.
- A file is read from, first that exists: the folder's copy, the folder's `.dist`. The root names
  (`<id>-brief.md`, `<id>-cases.md`) are not read; the owner's copy moved into the folder once.
- `roles.master.brief` defaults to `roles/master/brief.md`; a brief named and missing still refuses the start.
- A `role.yml` copy layers over its `.dist` per key, nested maps too, a list replacing whole (owner 2026-11-04).
- A role's `role.yml` declares `jobs:` and `readSteps:`; the chart reads both from the installed roles, none from code.
- A role's config defaults sit in its `role.yml`; `jagt.yml` `roles.<id>.*` overrides them per key, and a project's
  `roles.<id>.*` overrides that. The board shows each value's origin.
- Eval cases are data: `roles/<id>/eval/*.yml`. The Master's Java cases move there; the harness code stays a source set.
- The arbiter stays in code (0122): its folder carries files and defaults, never its rights.

## Rejected

- 0123's location map for every kind of file: no installed app needs it yet; roles are what users add.

## Reopen when

- A desktop install needs state, logs or stats outside jagt's root: 0123's map comes back for those kinds.
