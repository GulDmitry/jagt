# The board's marks and elements

[← design](design.md)

## The marks

**A wait on you says why in a glyph, the same on a card and in the header** (`stop.kind`, `BoardStopKindsTest`):

| why | badge | state | counted in `.waiting` |
|-----|-------|-------|-----------------------|
| a plan or round to read, a verb to press | `.badge.required`, no glyph | `.status` | yes |
| broken: a red run, an outside condition (`BROKEN`) | `.badge.problem` `!` in `--danger` | `.status`, no ring: a read ends it | yes |
| jagt stopped it (`GUARD`) | `.badge.stop` `■` | `.status.stalled`, `--you` ring | yes |
| you paused it (`PAUSE`) | `.badge.paused` `‖` | `.status.paused`, dashed `--muted` | no |
| header | `.chip.bad`: a start cut short, a job failed, reach lost | `.chip.stalled` `■`: `act` stopped | `.chip.paused` `‖`: the board |

- card edge, 3px — `article.you` / `.agent` / `.ci` / none, a `.row`'s own left border; `.optional`, the quiet tier, drops the colour.
- `.status` chip — the state in words, its age inside it; `.status.live` green: the work is on a shared branch.
- `--you` ring, also `.mr-age.stalled` / `.repos.stalled` — nothing will look again on its own: a poll ended, or a guard.
- `button.again` `↻` after its label, `--ok` border (a ring on the primary): it ran and what it did is live.
- `button.quiet` dashed `--muted` — pressed and changed nothing; ends when the card moves or it is pressed again.
- dotted underline — `a.id`, `a.mr-age`, `a.task-link`: the text opens something.
- `.checks`, 9px — `.green` a filled disc, `.red` a filled square, `.running` a hollow ring carrying the board's one
  `@keyframes` (`prefers-reduced-motion` safe); hollow means waiting everywhere, a said line or a retry pending.
- `✓` on `.mr-age.approved`, or `.tick` closing `.repos`: approved.
- in a round's replies — `.block` head, its `.verdict` word green FIXED / `--you` a question / grey pushed back; `.quote` muted.
- `.tabs` — an `--accent` underline means open, nothing else; a `.badge.required` `new` pill on the Drafted tab:
  replies since you last opened it.
- `button.flag` ⚐/⚑ — flagged important, this browser only; its row leads its group, its `.flagged .alias` chip carries ⚑.
- `.group.yours .group-head` on `--you-bg` — the rows waiting on you, above the rest.
- bold `.row-title.changed` — its state moved since you last had it open.
- `.doc .changed` bold with a left bar, `.doc .removed` muted struck — a plan line new, changed or gone since your approval.
- `button.row-verb`, a terminal glyph — a row's `onRow` verb, `primary` filled only where it is your move.
- `.detail.problem` in `--danger` / `.detail.you` in `--you` — the card's own line: broken, your move.
- `.waiting` pill on `--you-bg`, in the header — how many cards interrupt you; a pause is not one.
- `button.chip.log` — a running model role, a `▸` before its name; `.on` in `--accent` while its log stands in the pane.
- `.badge.required` on `--you-bg` — the act waiting on you now; `.badge.optional` on `--chip-bg`, whenever you like.
- `.log-time` in `--muted`, UTC, newest first, `.log-line` blocks 6px apart, a wrapped line hanging 9ch under its time;
  `<UTC time> <level> <message> <fields>`, `.log-level` `--muted`; `.log-line.danger` a WARN or ERROR entry in `--danger`.
- `td.last.failed` in `--danger` — a job's last run failed.
- toast edge, 3px — `.toast` `--ok`, `.toast.error` `--danger`: the verb ran, or was refused.
- a verb's key drawn after its name (`data-key`); `button.apart` muted at the row's end until hovered.
- `body.stale` at half opacity — the backend is gone and nothing on the page runs; `.chip.bad` says so.
- the tab's title `(n) jagt` and an icon — a red `!` something broke, else an amber dot something waits.
- absence — no dot, no tick, no badge: the expected state, the hover saying which. **Absence is the cheapest mark**:
  a fact earning neither height nor colour goes in `data-tip`, not `title`.
- transient, no legend row — `#palette-state.ok`/`.bad`, a said line's wait.

## The other elements

Every other `app.css` class, once: what it is, its token, when it shows.

| element | is | token | shows |
|---------|----|-------|-------|
| `.role-chips`, `.search`, `.menu`, `.grow` | the header: the role chips' slot, laid out as its children; the filter box; a button and the list it opens (Reports, the gear); a spacer pushing what follows to the row's end | `--panel`, `--accent` on focus | always; `.grow` in dialog feet too |
| `.phase`, `.count`, `.sep`, `.order`, `.clear-filters` | the phase bar: a filter per phase, its count (a group's too), the dot between items, the sort toggle, the reset | `--chip-bg` pressed, `--text`, `--muted` | the list's head; the reset while a filter is on |
| `.empty` | a place holding nothing: no task yet, a filter matching none, a phase at zero | `--muted` | nothing to show |
| `.selected`, `.pick`, `.row-line`, `.row-facts` | a row: the one the cursor is on, its button opening the task, its first line (alias, title, badge), its second (status, age, project) | `--chip-bg` selected, `--accent` focus, `--muted` facts | every row |
| `.keys` | the key hints under the list; the Session tab's key buttons | `--mono`, `--muted`, `--line` | always; a session window |
| `.card-top`, `.heading`, `.title`, `.meta` | a card's first line (alias, id, badge), its flag and title (16px in the pane), its fact line (status, request, checks, project) | `--muted` fact line | every card |
| `.actions` | a card's verb row, `data-group` `flow` before `tool` | `--muted` tool labels | a card with verbs |
| `.age` | how long a state has stood, inside its chip or the history | `--mono`, `--muted` | every status |
| `.open`, `.back` | on `main#board`: a task is open, and under 1200px its pane replaces the list; `.back` returns to it | — | under 1200px |
| `.history` | the open task's statuses, oldest first | `--mono`, `--muted` | the Overview tab |
| `.round`, `.screen` | the Round tab's drafted replies; a block holding a session's screen, a round or a diff | `--chip-bg`, `--mono` | the Session, Round and Diff tabs |
| `.doc-names`, `.diff`, `.mermaid` | the documents to choose from; the Diff tab, per repository; a diagram, its source until drawn | `--accent` pressed, `--muted`, `--mono` | the reading tabs |
| `.dock`, `.peek`, `.say` | the pane's foot: the agent's last screen line, the form sending the session a line | `--bg`, `--mono` | a session window |
| `.log-scroll`, `.log-lines`, `.log-head` | a log: its scroll box, its entries, what precedes the first entry or its empty note | `--muted` | a log is shown |
| `.report-head`, `.dialog-foot` | a dialog's title bar with its close; its foot, the state slot then the submit | `--panel`, `--line` | every dialog |
| `.field`, `.field-row`, `.checkbox`, `.choice`, `.hint` | a form: a labelled input, inputs side by side, a labelled checkbox, a radio group of four options at most (`ui/choice.js`), a setting's default-or-set note | `--muted`, `--chip-bg`, `--accent` chosen | dialogs, the gear, settings |
| `.checks-list` | a new project's check lines, hidden when empty | — | adding a project |
| `.jobs`, `.job-id`, `.jobs-table` | the Jobs report: its table, a job's id, the body holding it, which hides the plain text | `--line`, `--chip-bg`, `--mono` | the Jobs report |
| `.muted` | secondary text | `--muted` | anywhere |
| `.legend`, `.legend-head`, `.legend-key`, `.legend-says`, `.legend-edge` | the Help legend: its heading, a row's inert samples, a row's meaning, a card edge drawn as a sample | `--line`, `--muted`, `--mono`, an owner's token on the edge | the Help report |
| `.scrolls` | on `#tip`: a hover taller than the screen takes the pointer, to scroll | — | a long hover |
| `.login` | the login page's form | — | signed out |
