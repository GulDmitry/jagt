## Why

Only projects have a form. A key outside them is edited in `jagt.yml` with the human's editor, an inline entry is refused with "change it by hand", and a failed write tells the human to fix the file by hand.

## What Changes

- The board's gear opens one settings page: projects, review and deploy, tracker, Master mode, notifications; no role or model.
- A setting is changed as lines: its value moves, its comments and the key order stay; an inline entry becomes a block.
- A failed write restores the last good file and reports why as a typed failure; no text says "by hand" any more.
- Decision 0200: no YAML library keeps the layout, so jagt edits the file line-wise.

## Impact

- `project-setup`: one requirement renamed and modified, two added. `settings`: new capability (the board half is built after the writer).
- Out of scope: roles and models, secret values (only env var names), keys the page does not list.
