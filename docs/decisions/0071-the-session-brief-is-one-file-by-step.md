# 0071 — The session brief is one file, a section per step

[← decisions](README.md) · rule: [`session-brief`](../../openspec/specs/session-brief/spec.md),
[`task-flow`](../../openspec/specs/task-flow/spec.md#requirement-every-deploy-is-checked-where-it-landed)

## Status

Accepted 2026-10-23.

## Context

The Master judges by one file a human copies and edits. A session's texts were spread over a resource in the jar,
two keys of `application.yml` and strings in the classes relaying them; the deploy step, a string, told every
session to test its change by any means, and only a rebuild changed it.

## Decision

- What jagt tells a session lives in `session-brief.md.dist`, one `## <step>` section per step jagt sends.
- A human copies it to `session-brief.md`, gitignored; nothing copied, the `.dist` is read — onboarding stays one file.
- A section the copy lacks is read from the `.dist`, so a step shipped later reaches an old copy.
- An empty section tells the session nothing at that step.
- The facts of a step (task, commits, paths) stay jagt's and precede the section; the section is only the ask.
- Steps move into the file one at a time, `deploy` first, each with its own spec change and eval run.

## Rejected

- One `jagt.yml` key per text: a prompt is prose, and a key per step scatters it.
- Moving every step in one commit: a red eval could not name the step that broke it.

## Reopen when

- A step needs a fact only the human's text could place, so a section must be a template.
