## Why

`run`'s line rode the `project` argument of `press_task` and the flow engine, marked by `takesLine()`: one verb's meaning inside another's slot.

## What Changes

- `press_task` takes a `line` argument for the verb that types one; `project` names a project only.
- The flow engine, the command service and the Master's press carry the line apart from the project.
- `run` given a project, and any other verb given a line, is refused.

## Capabilities

### Modified Capabilities

- `board-verbs`: the line of `run` travels in its own argument.
