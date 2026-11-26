# The board's design code

[← AGENTS.md](../../AGENTS.md)

**The storybook is the board's own `Help` report** (AGENTS.md "Interfaces": one legend row per mark): `board/ui/legend.js`.
Every legend row is a mark, bar a **transient** one inside an open control
(`#palette-state.ok`/`.bad`, a said line's wait). The COST of adding one: [`board`](../../openspec/specs/board/spec.md).

**`--ok` and `--you` on one card do not
disagree**: APPROVED wears both — the approval landed, the deploy is yours.

## The colour palette

Fourteen tokens, each defined once at the top of `app.css` as `light-dark(light, dark)`: the gear's theme choice
(`data-theme` on `<html>`) and `prefers-color-scheme` both only set `color-scheme`, so a token cannot be in one
theme only. `--on-accent` is `button.primary`'s label (`#fff`, `#10131a` dark): accent text on the accent fill is
illegible. A hex outside `:root` is a bug (`css-hex-outside-root`); the translucent blacks are not hex: six shadows, two backdrops.
Text clears 4.5:1 and a control's edge 3:1 on `--panel` in both themes (`PaletteContrastTest`).

- `--you` `#92400e` / `#fbbf24` — **your move**: nothing advances without you.
- `--agent` `#1d4ed8` / `#93b4ff` — **an agent is working**.
- `--ci` `#6d28d9` / `#c4b5fd` — **the reviewers own it**: out of your hands, not blocked.
- `--ok` `#166534` / `#86efac` — **nothing wrong here**: a step taken, or a thing working.
- `--danger` `#b42318` / `#fca5a5` — **broken**: a failed run, a refused command, an outside condition holding a task.
- `--accent` `#1d4ed8` / `#93b4ff` — **pressable / pressed**: hover, a primary button, a pressed filter, `button.link`, the open tab.
- `--muted` `#5f6672` / `#9aa3ad` — **not news**: labels, ages, a verdict with nothing wrong, a pause you made.
- `--text` / `--line` / `--panel` / `--bg` / `--chip-bg` / `--you-bg` — structure, never meaning. A control's edge
  (input, select, switch, button) is `color-mix(in srgb, var(--text) 55%, var(--panel))`: `--line` is 1.4:1.

**A green REQUEST is an approval and nothing else**; the checks verdict wears its own DOT. **`--agent` and
`--accent` are the same blue**: never in one row, never two meanings on one element.

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
- `button.flag` ⚐/⚑ — flagged important, this browser only; a flagged row leads its group.
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

## Adding one

1. Spend **shape** before colour: one fact per element, one per hover.
2. Give it its legend row.
3. A colour nothing above covers means that meaning already exists under another name.

## Type, metrics and both themes

One family (`ui-sans-serif, system-ui`), one mono (`ui-monospace, SFMono-Regular, Menlo`) for anything compared
digit by digit: ages, aliases, token counts, report bodies. Sizes: 15px `h1`, 14px body, 13px header
controls and report meta, 12px card meta, detail, buttons, chips, 11px badges, status, `MR` age, tool-row
buttons, legend heads.

Radii climb with size: `999px` pills, `50%` dots, `6px` a control, `8px` a
card or toast, `10px` the report dialog. Grid gap 12px, card padding 10px, dialog padding 14px, meta gap 8px.
Shadow lifts a thing OFF the page and nothing else (reports menu, the dialogs, log button, toast, tip); its one exception
is not depth: the 2px `--ok` ring on a primary deploy button.

**No build step** (AGENTS.md): every mark is a border, a background or a character in the jar.
A library is a vendored file the jar serves as published, pinned by `BoardAssetsTest`
([0046](../decisions/0046-the-board-renders-preact-components-with-no-build.md)).

**No mark is told apart by colour alone**: two marks in one legend row differ in shape or glyph
(`BoardLegendNotColourAloneTest`); a chosen value too: the gear's and the settings' radios wear a check glyph (✓) and `aria-checked`.
