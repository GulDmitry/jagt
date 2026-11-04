## Why

A deploy handed back for a red pipeline stayed `DEPLOY_CONFLICT` forever when that commit's pipeline was flaky and later pipelines
on the deploy branch, containing the commit, were green: nothing read a pipeline again while the conflict stood.

## What Changes

- While a deploy conflict is held by red pipelines alone, jagt reads the newest finished pipeline on each held repository's deploy branch whose commit contains the deploy commit.
- Green ends the conflict and the task is `DEPLOYED` again; red, none or unread leaves it standing, with no re-run and no worktree opened.
- A merge or revert conflict is never ended by a read.

## Capabilities

### Modified Capabilities

- `deploy-check`: a red-pipeline deploy conflict ends on a later green pipeline containing the deploy commit.
