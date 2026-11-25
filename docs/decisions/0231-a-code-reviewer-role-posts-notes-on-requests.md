# 0231 — A code-reviewer role posts notes on requests

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "The human in the loop" ·
design: [`roles/code-reviewer/design.md`](../../roles/code-reviewer/design.md)
on [0122](0122-a-role-is-a-module-the-chart-composes.md), [0176](0176-every-kind-has-a-ceiling.md), [0203](0203-a-roles-words-stay-in-its-modules.md)

## Status

Accepted 2026-11-23 by the owner's order. Amends [0091](0091-the-shipped-master-reads-three-roles.md)
and [0148](0148-jagt-reaches-the-code-host-through-its-cli-login.md), both marked "amended by 0231".
Amended 2026-11-24: the role reads a task's diff before its ship ([`code-review`](../../openspec/specs/code-review/spec.md)).
Amended by [0240](0240-the-reviewer-never-posts-on-a-tasks-own-request.md): no note on a task's request, `post.own` gone.

## Context

- A third-party request is read by no one in jagt, and a session's code is read once, by the Master.
- The owner wants a role, on by default, that reviews both, answers review bots and learns from likes and dislikes.
- 0148 names the code-host writes; a note is none of them. 0091 fixes the Master's brief at three roles.
- 0122 knows two role shapes, arbiter and checker, both with a region; this role changes no task status.

## Decision

- **The seventh code-host write is a note**: on a jagt task's request on by default (`post.own`), on another person's
  request only with `post.others` in `roles/code-reviewer/review.yml`. Never an approval, a merge, a close or a push.
- **Third role shape: a job with no region.** A ledger row keeps a third-party request; no task opens, none moves, the board shows nothing.
- **One class posts** (`NotePoster`, row `note-writers`); each note opens with the AI line and carries a hidden key read back before a post.
- **The Master's brief gains an `unless` column** (amends 0091): a row is skipped while the role is on and the project has a code host.
- **0148 holds for the login, not for the list of writes**: the writes are those `AGENTS.md` names, a note the seventh.
- **Ceilings raised by name** ([0176](0176-every-kind-has-a-ceiling.md)): `roles` 4 to 5, `spec-capabilities` 52 to 53.
  Ports, startup checks and source sets rise by one each in the task building them, to the number it measures, under this record.
- `RoleWords` matches a hyphenated role id, which camel-case splitting hid.

## Rejected

- A region and a status for the review: the review is advisory and gates no move, so a status would name an exit it cannot have.
- A task per third-party request: it opens from a model-free source alone, and a request no one asked jagt to own is no task.
- Posting on another person's request by default: a note there is the human's to invite.
- Approving: the port has no approve method and no model's word is an approval.

## Reopen when

- A note is posted twice, or on a request it was not asked to read.
- The ratings show a role's notes disliked more than liked over a month of reads.
