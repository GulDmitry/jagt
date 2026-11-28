# The board's design code

[← AGENTS.md](../../AGENTS.md) · [marks and elements](design-elements.md)

**The storybook is the board's own `Help` report** (AGENTS.md "Interfaces": one legend row per mark): `board/ui/legend.js`.
Every legend row is a mark, bar a **transient** one inside an open control
(`#palette-state.ok`/`.bad`, a said line's wait). The COST of adding one: [`board`](../../openspec/specs/board/spec.md).

**The style book draws every component in every state, in both themes**: `board/styleguide.html`, its fixtures in
`board/styleguide/`, a card per status and per stop trigger the backend sends (`BoardAssetsTest`), and the legend.

Each class and token in `app.css` is described once, here or in [marks and elements](design-elements.md):
its meaning, its token, when it is used. `board/test/inventory.js` lists them.

**`--ok` and `--you` on one card do not
disagree**: APPROVED wears both — the approval landed, the deploy is yours.

## The colour palette

Fourteen tokens, each defined once at the top of `app.css` as `light-dark(light, dark)`: the gear's theme choice
(`data-theme` on `<html>`) and `prefers-color-scheme` both only set `color-scheme`, so a token cannot be in one
theme only. `--on-accent` is `button.primary`'s label (`#fff`, `#10131a` dark): accent text on the accent fill is
illegible. A colour outside a token is a bug (`css-colour-outside-token`).
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
- `--shade` black in both themes, never alone: mixed translucent into the six shadows and two backdrops, and the terminal's screen.
- `--mono` the one monospace family, not a colour.

**A green REQUEST is an approval and nothing else**; the checks verdict wears its own DOT. **`--agent` and
`--accent` are the same blue**: never in one row, never two meanings on one element.

## Adding one

1. Spend **shape** before colour: one fact per element, one per hover.
2. Give it its legend row, its style-book frame and its line in [marks and elements](design-elements.md).
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

## The board's code design

- A component is a Preact function in `components/` written with `htm`; it renders props and reads no `ui/` module.
- `ui/` wires the page: it reads the store, paints, and passes facts down as props.
- One stylesheet, `app.css`, holds the board's rules; a lazily loaded part brings its own (`terminal/`, `viewer/`).
  The style book's own rules live in `styleguide/styleguide.css`, never in `app.css`.
- A rule colours through `var(--token)`; a class names what an element is, and a value from the wire is a `data-` attribute.
- One verb wears one design wherever it is drawn: its class is `verbClass` on a card, `quickClass` on a row.
- A file imports five modules at most and holds no mutable module state (`eslint.config.js`).
- Each holds: every `app.css` class is used and described, every component is drawn (`board/test/stylebook.test.js`);
  every class is shown in both themes, bar the header, dialog and login ones still owed a frame, and one verb wears
  one design (`BoardStyleguideTest`).
