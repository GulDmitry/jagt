## Why

A human writes scenarios with acceptance before a deploy, over several rounds; the headless read cannot ask. One
interactive session per project, in its workspace, gives it a place to.

## What Changes

- A global command `acceptance` and a task verb `scenarios` open one interactive acceptance session per project, cwd its
  workspace.
- `scenarios` loads the task's ticket and any open acceptance question.

## Impact

- Spec: `acceptance-workspace`.
- `GlobalCommand` and `Move` beans, the board's buttons and legend, the runtime's session start.
