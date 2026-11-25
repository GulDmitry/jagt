# 0240 — The code reviewer never posts on a task's own request

[← decisions](README.md) · rule: [`code-review`](../../openspec/specs/code-review/spec.md) ·
design: [`roles/code-reviewer/design.md`](../../roles/code-reviewer/design.md)
on [0162](0162-a-check-holds-for-all-code.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-24, by the owner's order (JAGT-270). Amends [0231](0231-a-code-reviewer-role-posts-notes-on-requests.md).

## Context

- 0231 let the reviewer post on a task's own request by default (`post.own`).
- On 2026-11-24 it posted under the human's account one minute after the Master shipped, a finding the session owned before the ship.
- Once the tree is read before the ship, `post.own` decides nothing: the pre-ship read never posts.

## Decision

Why: the session owns a finding before the ship, so a post on its request is a second voice under the human's account.

- The reviewer reads a task's tree before every push and relays each finding to the session; it posts nothing on a request a task holds.
- On a request no task holds it posts and answers bots only with `post.others`, off by default.
- `post.own` is removed from `review.yml`, its parsing and its documentation; the Master's brief row yields to the reviewer
  while the role is on and the project has a code host, whatever the reviewer's settings.
- A reply the Master's ship posts opens with the AI line, and its ratings are recorded beside the notes' in
  `records/code-review.jsonl`, each line naming its kind (`note` or `reply`).
- **Ceiling raised by name** ([0176](0176-every-kind-has-a-ceiling.md)): `spec-capabilities` 53 to 54, for `review-actors`.

## Rejected

- Keeping `post.own` as a switch: a key whose every value gives the same outcome is a lie in the file.
- Posting the reviewer's finding on the request when the read fails: the session owns it before the ship; it is relayed.
- Counting a reply's rating in a separate file: the thumbs on a note and on a reply answer one question, how jagt's posts were received.

## Reopen when

- The session can no longer receive a finding before the ship, so a request gets its first read after it.
