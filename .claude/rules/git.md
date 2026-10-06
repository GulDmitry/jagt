---
paths:
  - "**/service/GitService.java"
  - "**/capability/deploy/**"
  - "**/capability/ship/**"
  - "**/service/TaskProvisioning.java"
---

The base branch is read-only. The only writes to a shared branch are `deploy` and `revert`. No git hooks, ever.

Full rules for git safety: **[`openspec/specs/git/spec.md`](../../openspec/specs/git/spec.md)** — read it before changing behaviour here.
