## Why

A repository holding no work stays on the card and in the checks when it is the task's primary one.

## What Changes

- Where one repository has a request, every repository holding no work and no request leaves the task, the primary one included.
- A primary that leaves moves the task's home to the repository that comes first, for the session's next start.
- A primary stays while an agent runs in its checkout; the next read tries again.
- A task's only repository stays though it holds no work.

## Capabilities

### Modified Capabilities

- `repo-attach`: a repository holding no work leaves the task.
