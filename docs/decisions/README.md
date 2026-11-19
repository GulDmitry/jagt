# Decisions

[← AGENTS.md](../../AGENTS.md)

One file per ARCHITECTURAL decision — jagt's rings and seams, where state lives, how sessions run, what owns a
move: costly to reverse, or one a newcomer would undo as wrong. Never a feature's rule or parameter.
The rule lives in `openspec/specs/` or `docs/rules/`; the record, its evidence and alternatives.

- Named `NNNN-what-is-decided.md`, numbered in order, never renumbered.
- **Never edited once accepted**, typos aside: a change is a new record, marking the old `superseded by NNNN`.
  Accepted is the commit that lands its last step; until then the record's own steps amend it.
- Sections: **Status** (date, state), **Context** (what forced it, with numbers),
  **Decision**, **Rejected** (each with why), **Reopen when** (the measurement changing it); at most 50 lines, else two.
- **Linked**: a record names its rule first; the index pages list all (`DecisionIndexTest`).
- Taken late: `recorded retroactively`, only what history proves.

## Index
- [0001-0099](index-0001-0099.md)
- [0100-0199](index-0100-0199.md)
- [0200-0299](index-0200-0299.md)
