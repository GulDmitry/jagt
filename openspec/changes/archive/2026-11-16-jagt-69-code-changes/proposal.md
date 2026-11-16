## Why
JAGT-69 steps 4 and 5: the Code tab shows the change beside its source, and no editor is a default.

## What Changes
- The Code tab has a Changes list: each changed file with +/- counts; one opens as a side-by-side merge view of its old and current text.
- The list reads `/api/tasks/diff`, the Diff tab's own source.
- `editorCommand` has no default in `jagt.desktop.yml.dist`; unset, `code` opens the viewer alone and startup raises no problem.

## Impact
- Specs: board-diff, platform
