---
paths:
  - "**/service/Git*.java"
  - "**/service/WorktreeInspection.java"
  - "**/service/DiffCheckouts.java"
  - "**/capability/deploy/**"
  - "**/capability/ship/**"
  - "**/service/TaskProvisioning.java"
  - "**/service/NewTaskWorktrees.java"
  - "**/surface/agent/*Gate*.java"
  - "**/surface/agent/GitReadLine.java"
  - "**/service/ReadScopes.java"
---

The base branch is read-only. The only writes to a shared branch are `deploy` and `revert`. No git hooks, ever.

Full rules for git safety: **[`openspec/specs/git/spec.md`](../../openspec/specs/git/spec.md)** — read it before changing behaviour here.
