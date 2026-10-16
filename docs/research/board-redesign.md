# The board as a workspace — research, 2026-10-16

Seen on the running 0.9.4 board, fictional tasks injected into the page. Mockup with three artboards (today,
proposed, add a project): https://claude.ai/artifact/1ot4ZnnVavMibvwMyzZz2u

## 1. TL;DR

1. **From a card grid to a split view**: a task list on the left, and the selected task on the right.
   Clicking a row opens the task. What lives in hovers today moves to the task pane.
2. **A row has a fixed height and keeps its place.** Rows are grouped by project, in a stable order.
   Phase columns stay rejected for the reason `header.js` gives: a card that moves has to be found again.
3. **Jobs, Activity and the other reports move to a bottom panel**, the way an IDE has tool windows.
   The machine's state (push link, Master mode, review poll, failed jobs) moves to a status bar.
4. **The launch row and Ask become one input** (⌘K), and New task (⌘N) opens a short form. Three text boxes become one.
5. **A project can be added from the board.** The dialog checks the path and writes `jagt.yml`, which stays the one source.
6. **The host stays a browser window**: `chrome --app=http://localhost:8290` ([desktop-host](desktop-host.md)).

## 2. What the running board shows

| # | finding | where |
|---|---------|-------|
| 1 | the two tasks that need you sit apart; row order is insertion order, and rows are ragged by card height | `main` grid `auto-fit` |
| 2 | 28 tool buttons on seven cards; `Restart agent` weighs as much as `Diff` | `card.js` tool row |
| 3 | `Done` kills the session and deletes the worktree, and looks like any verb (blue as a primary) | `TaskAction.DONE` |
| 4 | a click on a card does nothing; the status history, next poll and checks are only in hovers | `meta.js` `data-tip` |
| 5 | the launch form is on screen all day; the project picker says `0 selected` over eleven, ⌘-click to pick several | `index.html` `#launch` |
| 6 | launch row, Ask and filter are three text boxes; `do` in Ask and Start answer one question | `#launch`, `#palette` |
| 7 | Jobs is a `pre` dump: sixteen internal ids, columns slip on long ids, no on, off or failed | `JobsCommand` |
| 8 | `master: act` presses your buttons, and only the Master report says so | header |
| 9 | six report buttons of equal weight, right after the filter; Help is one of them | header |
| 10 | the phase line is a filter that reads as plain text | `#phases` |

## 3. The proposed layout

```
┌ jagt  [2 need you]                       [ filter or a command  ⌘K ]  [New task ⌘N] ┐
├──┬───────────────────────────┬──────────────────────────────────────────────────────┤
│▣ │ BILLING-API 3             │ a5 ABC-70 Dark mode for the settings page            │
│▢ │▌a1 Refund webhook  NEEDS… │ checks failed 6m · web-portal · ABC-70 → origin/main   │
│◷•│▌a2 Invoice PDF …          │ [Check review r] [Ship s] | Focus Open IDE   Done… ⇧X │
│▥ │ WEB-PORTAL 4              │ Overview | Diff 3 files | Session | Review round 3   │
│  │▌a5 Dark mode …   PROBLEM  │  PROBLEM  2 tests fail …                              │
│  │▌a4 Login form …  deploy   │  Timeline           What watches this task           │
├──┴───────────────────────────┴──────────────────────────────────────────────────────┤
│ Jobs | Activity                                                                  × │
│ YOUR TASKS  ● Verify  verify  … failed 6m ago · a5                                  │
├────────────────────────────────────────────────────────────────────────────────────┤
│ ● connected   Master: act — presses for you   review poll on   1 of 16 jobs failed │
└────────────────────────────────────────────────────────────────────────────────────┘
```

- **Row**: the edge colour (owner), alias, title, badge; then ticket, status with its age, `MR`, checks dot,
  and the job touching it now. Two lines, always.
- **Task pane**: the verbs for the next step on top, and the tool verbs after a divider. `Done…` sits apart,
  in `--danger` text, and asks to confirm.
  Tabs: *Overview* (the line under the card, the timeline, what watches the task, spend), *Diff* (desktop-host §3A),
  *Session* (the session's last output read with `tmux capture-pane`, plus the existing say line), *Review round*.
- **Jobs panel**: grouped by what each job serves (your tasks, Master, tracker, housekeeping). A row has a human name
  plus the id, what it does, its last result and the task it touched, and when it runs next. Off and idle get a hollow dot.
- **Keys**: `j`/`k` move between rows, `Enter` opens one, and each verb shows its letter. Every row and tab
  has a URL hash, so a notification opens `#a5` directly.

## 4. Rules this touches — each needs a yes

| rule | today | the change |
|------|-------|------------|
| `board`: a report opens in a `<dialog>`, never a page | modal over the board | a bottom panel; a dialog only for confirmations |
| `header.js`: no phase columns | phase filter line | kept: list grouped by project, the phase as a count |
| AGENTS: a card must not grow taller | card height varies by text | a row is fixed; the pane takes what grew |
| AGENTS: a countdown belongs in a tooltip | next poll in a hover | in the pane, which is opened on demand |
| AGENTS: every option lives in `jagt.yml` | edited by hand | still: the dialog writes the file; a decision record |
| `board`: the launch row's pickers | a form always on screen | the same fields behind ⌘N |
| AGENTS: never two controls for one question | Start and `do` | one input |

## 5. Order, cheapest first

1. **No layout change**: tool verbs behind the selection, `Done…` set apart, a checkbox list for projects, the
   Jobs report as a grouped table, the Master mode in the header. Each is a `boardTest` case.
2. **Split view**: the list, the task pane, the hash route. The card builder becomes the row plus the pane.
3. **Tabs**: Diff and Session first; each is one read endpoint.
4. **Bottom panel and status bar**: reports leave the dialog; the header loses its six buttons.
5. **Add project**: `POST /api/projects` writes `jagt.yml`, and the startup checks run on the new entry.

## 6. Open

- Group the list by project or by whose move it is? By owner puts your tasks on top, but a row moves when its owner changes.
- Does the bottom panel stay open by default, or open only on a failed job?
- Should the Session tab show the output live (SSE), or only on open?
