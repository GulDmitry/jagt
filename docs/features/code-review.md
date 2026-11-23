# Code review
[← Features](../features.md) · Each line names one thing the code reviewer does and links to the requirement that defines it.

## Code review · [spec](../../openspec/specs/code-review/spec.md)
- **Review once per head** — A request's diff, tests and description are read once per new head, on by default, with no task and nothing on the board.
  [spec](../../openspec/specs/code-review/spec.md#requirement-the-code-reviewer-reads-a-requests-change-once-per-head)
- **Notes that say who wrote them** — Each note opens with the AI line and is posted once; on another person's request only with `post.others`.
  [spec](../../openspec/specs/code-review/spec.md#requirement-a-note-is-posted-once-and-says-who-wrote-it)
- **Bots answered, bounded** — A review bot's note gets one answer, at most three per thread.
  [spec](../../openspec/specs/code-review/spec.md#requirement-review-bots-are-answered-bounded-per-thread)
- **Ratings recorded** — Likes, dislikes and resolutions are appended as dated statistics.
  [spec](../../openspec/specs/code-review/spec.md#requirement-likes-dislikes-and-resolutions-are-recorded-as-dated-statistics-per-change)
