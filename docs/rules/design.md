# The board's design code

[← AGENTS.md](../../AGENTS.md)

**The storybook is the board's own `Help` report**: `static/ui/legend.js` renders every mark below as the
page's own element, and every legend row is a mark — bar a **transient** one inside an open control
(`#palette-state.ok`/`.bad`, a said line's wait). The COST of adding one: [`surfaces.md`](surfaces.md).

Also marks: every sample `legend.js` renders. **`--ok` and `--you` on one card do not
disagree**: APPROVED wears both — the approval landed, the deploy is yours.

## The colour palette

Thirteen tokens, at the top of `app.css` and again under `prefers-color-scheme: dark`: a token in one scheme
only is a hole in the other, and the dark block holds one *rule*, `button.primary`'s label (`#fff`, `#10131a`
dark, accent text on the accent fill being illegible). A hex outside `:root` is a bug — that label and the
translucent blacks (four shadows, the dialog backdrop) apart.

- `--you` `#b45309` / `#fbbf24` — **your move**: nothing advances without you.
- `--agent` `#1d4ed8` / `#93b4ff` — **an agent is working**.
- `--ci` `#6d28d9` / `#c4b5fd` — **the reviewers own it**: out of your hands, not blocked.
- `--ok` `#15803d` / `#86efac` — **nothing wrong here**: a step taken, or a thing working.
- `--danger` `#b42318` / `#fca5a5` — **broken**: a failed run, a refused command, a problem line.
- `--accent` `#1d4ed8` / `#93b4ff` — **pressable / pressed**: hover, a primary button, a pressed filter.
- `--muted` `#6b7280` / `#9aa3ad` — **not news**: labels, ages, a verdict with nothing wrong.
- `--text` / `--line` / `--panel` / `--bg` / `--chip-bg` / `--you-bg` — structure, never meaning.

**A green REQUEST is an approval and nothing else**; the checks verdict wears its own DOT. **`--agent` and
`--accent` are the same blue**: never in one row, never two meanings on one element.

## The marks

- card edge, 3px — `article.you` / `.agent` / `.ci` / none; `article.you.optional`, the quiet tier, drops the
  colour.
- status chip — `.status`: the state, its age inside it. `.status.live` green: the work is on a shared branch.
- green button — `button.again`, a ring on the primary one and a border otherwise: this verb ran and what it
  did is live, so pressing repeats it.
- dotted underline — `a.id`, `a.mr-age`, `.drafts`: the text opens something.
- 9px dot — `.checks.red` / `.checks.green` filled: a verdict is in. `.checks.running` hollow ring, carrying
  the board's one `@keyframes`: waiting, as hollow means everywhere — under a said line too. The ring says it,
  the pulse only draws the eye (`prefers-reduced-motion` safe).
- `✓` on the request, or `.tick` beside several: approved.
- in a round's replies — `.block` head, its `.verdict` green fixed / amber a question / grey pushed back;
  the `.quote` it answers muted.
- countdown text — `.pulse`, amber as `.pulse.stalled`: when the poll runs next; amber once nothing will look
  again.
- absence — no dot, no tick, no badge: the expected state, no pipeline, or nothing read yet, the hover saying
  which. **Absence is the cheapest mark**: a fact earning neither height nor colour goes in `data-tip`, not
  `title`.

## Adding one

1. Spend **shape** before colour: one fact per element, one per hover.
2. Give it a row in `legend.js` rendering the element itself, never a colour in words.
3. A colour nothing above covers means that meaning already exists under another name.

## Type, metrics and both themes

One family (`ui-sans-serif, system-ui`), one mono (`ui-monospace, SFMono-Regular, Menlo`) for anything compared
digit by digit: ages, aliases, token counts, report bodies. Sizes: 15px `h1`, 14px body, 13px header
controls and report meta, 12px card meta, detail, buttons, chips, 11px badges, status, `MR` age, tool-row
buttons, legend heads.

Radii climb with size: `999px` pills, `50%` dots, `6px` a control, `8px` a
card or toast, `10px` the report dialog. Grid gap 12px, card padding 10px, dialog padding 14px, meta gap 8px.
Shadow lifts a thing OFF the page and nothing else (report dialog, log button, toast, tip); its one exception
is not depth: the 2px `--ok` ring on a primary deploy button. **One animation exists**, `checks-pulse`.

**No build step, no CDN, no external asset**: every mark is a border, a background or a character in the jar.
