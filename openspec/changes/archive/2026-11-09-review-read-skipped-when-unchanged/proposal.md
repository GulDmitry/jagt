## Why

Every auto-review poll pays a model read of each open request, approved and deployed tasks included, though most polls
find the request as the last one left it.

## What Changes

- `CodeHost.requestFingerprint` reads what the host shows of a request cheaply: its state, head commit, checks and reviews.
- An auto-review poll whose fingerprint and unresolved note ids equal those of the last paid read keeps that read's verdict
  and pays nothing.
- A changed request, no fingerprint, a failed one, no earlier read, or a sweep anyone else asked for reads as before.
- `github` and `gitlab` give a fingerprint; `command`, `none` and `replay` give none.
- The last read is kept in memory: the verdict it keeps is not persisted either.

## Impact

- Spec: `review`.
- `CodeHost`, `CodeHostDriver`, `ChosenCodeHost`, `GitHubCli`, `GitLabCli`, `CommandPipeline`, `OfflineCodeHost`, `ReviewRound`.
- Decision 0156.
