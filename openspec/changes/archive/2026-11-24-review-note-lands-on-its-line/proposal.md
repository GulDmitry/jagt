## Why

Every inline note was refused on the code host, fell back to a top-level note, and a re-review after a push posted the same findings again.

## What Changes

- The merge-request driver sends an inline note's position as a JSON body.
- A refused line, or a finding with no diff line, posts no note; one WARN names the cause.
- A finding an open thread of the role's own already says is not posted again.

## Capabilities

### New Capabilities

### Modified Capabilities

- `code-review`: no top-level fallback; an open own thread is not repeated.
