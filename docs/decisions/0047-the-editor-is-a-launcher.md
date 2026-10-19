# 0047 — The editor is a launcher

[← decisions](README.md) · rule: [`runtime`](../../openspec/specs/runtime/spec.md), [`seams.md`](../rules/seams.md)

## Status

Accepted 2026-10-19.

## Context

- `EditorDriver` also held IntelliJ's recent-projects pruning, the folder names IDEs write, and the files copied into a worktree.
- Developers on a project use VS Code, IntelliJ, Vim and Sublime. A driver naming one leaks it into the seam.
- [0007](0007-only-adapter-names-a-vendor-or-an-os.md) lets only `adapter/` name a vendor, and the seam is the port.

## Decision

- The editor seam opens a worktree and a diff, through `editorCommand` and `editorDiffCommand`, and nothing else.
- IDE folder names live at the worktree level: `WorktreeNoise` holds the residue, `WorktreeFiles.PROJECT_FILES` the files copied.
- A copied path is taken when the base repository holds it; a file the worktree already holds is tracked and stays.
- `editorDiffCommand` is optional: unset, jagt starts and a diff is refused with a sentence naming the key and examples.
- The `idecleanup` job is gone with the recent-projects code.

## Rejected

- One driver per IDE: every new editor needs code, for a launch that is one command.
- A config key listing the folders: a default every project would repeat, for names that change with IDEs, not projects.

## Cost

- IntelliJ's recent-projects list keeps the entries of removed worktrees; the human clears them there.

## Reopen when

- An IDE writes service files that break a worktree opened without them.
- A project needs a folder list of its own that the constants cannot hold.
