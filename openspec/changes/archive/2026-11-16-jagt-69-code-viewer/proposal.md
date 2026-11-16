## Why
JAGT-69 step 3: with no editor configured, `code` has nothing to open; the board reads the worktree itself.

## What Changes
- A Code tab shows the open task's worktree: file tree, one file highlighted (CodeMirror, loaded on demand), a search over the worktree.
- Read-only endpoints `/api/tasks/code/{tree,file,search}`, confined to the task's worktree and capped.
- `code` selects the Code tab and, with no editor configured, opens no editor.

## Impact
- Specs: board-workspace, board-verbs
