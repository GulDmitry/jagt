## Why

What jagt tells a session at a step was written into its code, so the human could neither read nor change it; the
deploy step asked every session to test its own change by any means.

## What Changes

- What a session is told at each step is a `## <step>` section of `session-brief.md.dist`, copied to
  `session-brief.md` to edit; a section the copy lacks is the shipped one's, an empty one says nothing.
- The deploy step is the first one there; the shipped one checks that the deploy landed, not the feature.

## Capabilities

### New Capabilities

### Modified Capabilities

- `session-brief`: the brief is a file a human may copy and edit, step by step.
- `task-flow`: the deploy check says what the brief's `deploy` step says.
