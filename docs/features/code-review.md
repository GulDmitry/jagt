# Code review
[← Features](../features.md) · Each line names one thing the code reviewer does and links to the requirement that defines it.

## Code review · [spec](../../openspec/specs/code-review/spec.md)
- **Review once per head** — A request's diff, tests and description are read once per new head, on by default, with no task and nothing on the board.
  [spec](../../openspec/specs/code-review/spec.md#requirement-the-code-reviewer-reads-a-requests-change-once-per-head)
- **Read before the ship** — A task's diff is read at hand-back; each finding goes back as a review round and the ship waits.
  [spec](../../openspec/specs/review/spec.md#requirement-the-code-reviewer-reads-a-tasks-diff-before-its-ship)
- **Notes that say who wrote them** — A short AI line opens each note, three lines at most, posted once; others' requests need `post.others`.
  [spec](../../openspec/specs/code-review/spec.md#requirement-a-note-is-posted-once-and-says-who-wrote-it)
- **Bots answered, bounded** — A review bot's note gets one answer, at most three per thread.
  [spec](../../openspec/specs/review/spec.md#requirement-review-bots-are-answered-bounded-per-thread)
- **Ratings recorded** — Likes, dislikes, resolutions and the reason a human gave are appended as dated statistics.
  [spec](../../openspec/specs/code-review/spec.md#requirement-likes-dislikes-and-resolutions-are-recorded-as-dated-statistics-per-change)
