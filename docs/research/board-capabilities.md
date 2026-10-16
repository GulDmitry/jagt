# Everything the board does — inventory, 2026-10-16

What the redesign ([board-redesign](board-redesign.md)) must keep. Each row: the use case, where it lives today,
where it goes, and how it was checked on 2026-10-16. *Walked* means Chrome DevTools on the 0.9.4 jar with the
Preact card, fictional tasks in the page; *suite* names the `boardTest` class that pins it. Verbs come from
`Move.actions()` and reports from `GlobalCommand` beans, so no surface lists them; this table is a check, not a source.

## Per-task verbs (`TaskAction`)

| use case | today | redesign | checked |
|----------|-------|----------|---------|
| `focus` the agent’s terminal | tool row; primary where `Move` says | row button + pane | walked: press reached the server; `BoardActionsTest` |
| `sweep` (Check review) | flow row | pane flow row | walked render; suite |
| `ship` | flow row | pane flow row, `s` | walked render; suite |
| `deploy`, deploy again (green) | flow row | pane flow row, `d` | walked render; suite |
| `revert` — asks first | flow row + confirm | pane + confirm | `BoardActionsTest.revertingAsksFirst…` |
| `done` — asks first | flow row + confirm | pane, apart, + confirm | walked; `closingATaskAsksBeforeAnythingRuns` |
| `trust` git | flow row | pane flow row | walked render |
| `ide`, `diff` | tool row | pane tools; Diff tab | walked render |
| `respawn` | tool row | pane tools | walked render |
| a press in flight disables its verbs | `inflight` | same | styleguide state |

## Reports and global commands (`GlobalCommand`)

| use case | today | redesign | checked |
|----------|-------|----------|---------|
| `stats`, `activity`, `jobs`, `help`, `finished`, `master` | header buttons → dialog | bottom panel tabs | walked: all six open |
| `replies` offered on a card, with the say line | card tool row → dialog | Review round tab + say line | walked |
| `do` — launch | launch row; `do` in Ask | ⌘N form; ⌘K | walked: empty Start refused |
| `resume <url>` | Ask only | ⌘K | suite |
| `act` — give the Master its buttons back | `act stopped` chip | status bar, pressable | suite |
| `run <job>` now | Ask only | Jobs panel row button | **gap: no button today** |
| ⌘K tier 1 grammar, tier 2 plain words | palette | one input | walked tier 1; tier 2 not run (model call) |

## Board controls

| use case | today | redesign | checked |
|----------|-------|----------|---------|
| filter `/`, `Esc` clears | header box | the one input | walked |
| needs my action | checkbox | `N need you` button | walked |
| narrow to a phase; clear filters | phase line | list header counts | walked |
| order: added / alias | phase line | list header | walked |
| flag ⚐/⚑, this browser | card | row | walked |
| ticket, request and per-repository links | card meta | row + pane head | walked render |
| hover facts: status history, checks, next poll | `data-tip` | pane Overview | walked |
| deep link `?task=` from a banner | filter | hash `#a5` selects | walked |
| backend unreachable: dims, refuses input, reconnects | header chip | status bar | walked |
| session log | `log` button | bottom panel tab | walked |
| toasts: answer or refusal of a verb | corner | same | walked |
| counts: need you, auto-review, jobs failed | header chips | header + status bar | walked |
| empty and filtered-empty boards | two lines | same | suite |
| legend of every mark | Help | Help tab | walked |
| styleguide of every component state | — | `/styleguide.html` | walked, `BoardStyleguideTest` |
| press log | — | `interactions.jsonl` | walked: 26 presses, no task named |

## Walked again on the list and pane, 2026-10-16

Every row above was walked once more on the new layout, light and dark, at 1440 and 1024 px, with 24 tasks
(a repaint takes about a millisecond). Six defects were found and fixed, each with a board test that failed
without its fix: a stopped poll invisible on a closed row, the band hiding a row's project, the open task
scrolling away with a long list, the log button over the last row, two Tab stops a row, and a linked task
closing once its filter cleared.

## Gaps the mockup must close before it is built

- **`run <job>`** has no button: a Jobs panel row needs one.
- **The flag, the order toggle and the session log** are missing from the mockup.
- **Stats, Finished, Master and Help** must be tabs of the bottom panel; the mockup shows two.
- **A launch refusal speaks the palette's grammar** (`do <project…>`) to someone who used the form.
