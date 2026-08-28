# The board's design code

[← AGENTS.md](../../AGENTS.md)

The vocabulary the board is allowed to speak: every colour, every mark, and what each one means. What it COSTS
to add one is [`surfaces.md`](surfaces.md) — read that too before putting anything on the board.

**The storybook is the board's own `Help` report.** `static/ui/legend.js` renders every mark below as the
page's own element, so nothing here is a picture that can go stale. There is no second gallery, and the two
lists are one invariant: every MARK below has a legend row and every legend row is a mark below. Which
elements happen to wear a mark is this file's column, not a row count — the dotted underline is one mark on
three elements.

## The colour palette

Thirteen tokens, declared once at the top of `app.css` and again under `prefers-color-scheme: dark`.

A hex outside `:root` is a bug with two exceptions, both deliberate: `button.primary`'s LABEL is `#fff`, and
`#10131a` in the dark scheme, because green or accent text on the accent fill is illegible either way; and the
translucent blacks at `app.css:72, 74, 193, 199, 207` are the four shadows and the dialog's backdrop — depth is
not colour.

| token | light | dark | means, board-wide | worn by |
|---|---|---|---|---|
| `--you` | `#b45309` | `#fbbf24` | **your move** — nothing here advances without you | card edge, the `ask` badge, header waiting count, a stopped poll, drafted replies |
| `--agent` | `#1d4ed8` | `#93b4ff` | **an agent is working** | card edge |
| `--ci` | `#6d28d9` | `#c4b5fd` | **the reviewers own it** — out of your hands and not blocked | card edge |
| `--ok` | `#15803d` | `#86efac` | **nothing wrong here** — a step already taken, or a thing that is up and working | live status chip, the `again` deploy mark, the approved request, the multi-repo tick, the checks dot for a run that passed, the connection dot and the on-chips in the header |
| `--danger` | `#b42318` | `#fca5a5` | **broken** — a failed run, a refused command, a problem line | checks dot, `PROBLEM:` detail, error toast, failed-jobs chip |
| `--accent` | `#1d4ed8` | `#93b4ff` | **pressable / pressed** — hover, a primary button, a pressed filter | buttons, links, aliases |
| `--muted` | `#6b7280` | `#9aa3ad` | **not news** — labels, ages, a verdict with nothing wrong with it | meta row, checks dot for a passing or running run |
| `--text` / `--line` / `--panel` / `--bg` / `--chip-bg` / `--you-bg` | — | — | structure, never meaning | surfaces, borders, chip fills |

Three consequences worth stating, because each was learned the expensive way:

- **A green REQUEST is an approval and nothing else.** That is the 2026-08-26 lesson, and it survives: the
  checks went green again on 2026-08-26 on the owner's call, but on their own DOT — where the label already
  carries the approval, a green dot beside it cannot be read as one, and it is the same green the header spends
  on a connection that is up.
- **`--ok` and `--you` on one card do not disagree.** Green is what is already fine, amber is what is left: an
  APPROVED task wears both, because the approval landed and the deploy is still yours.
- **`--agent` and `--accent` are the same blue.** Deliberate: one is a card edge and the other is only ever a
  control, so the two never sit in one row — but they must not be given different meanings on one element.

## The marks

Colour is the cheapest channel and the first to be overspent, so shape carries as much as it can.

| mark | element | says |
|---|---|---|
| card edge, 3px | `article.you` / `.agent` / `.ci` / none, and `article.you.optional` for the quiet tier | whose move it is; the quiet tier drops the colour, so an alarm-coloured card is one that is stuck |
| badge, filled | `.badge.required` | you are the hold-up, and WHICH act is wanted |
| badge, grey | `.badge.optional` | yours whenever you like |
| status chip | `.status` | the state, with its age inside it — a bare duration between two separators reads as a fact of its own |
| green status chip | `.status.live` | its work is on a shared branch, said here because no verb on this card is the deploy |
| green button | `button.again` — a ring on the primary one, a border otherwise | this verb already ran and what it did is still live, so pressing it repeats it |
| dotted underline | `a.id`, `a.mr-age`, `.drafts` | this text opens something; solid on hover |
| 9px dot, filled | `.checks.red`, `.checks.green` | a verdict is in — red failed, green passed |
| 9px dot, hollow ring | `.checks.running` | still waiting for a verdict. Hollow is "waiting" everywhere it appears |
| pulsing ring | `.checks.running`, the one `@keyframes` on the board | a clock is running; what it says must survive `prefers-reduced-motion`, so the RING is what says it and the pulse only draws the eye |
| `✓` | on the request, or `.tick` beside several | approved |
| countdown text | `.pulse`, amber as `.pulse.stalled` | when the unattended poll runs next; amber once nothing will look again on its own, because that hands the move back |
| filled amber button | `.drafts` | answers are drafted and waiting; the line that announces them is what opens them |
| header chip | `.chip.on`, `.chip.bad` | whether anything polls at all, and what the unattended runs did |
| 8px dot | `.dot`, `.dot.on` | top right only: whether this page is being told about changes |
| absence | no dot, no tick, no badge | the expected state, no pipeline at all, or nothing read yet — and the hover says which |

**Absence is a mark and it is the cheapest one.** A fact that earns neither height nor a colour goes in
`data-tip`, which is one node placed on hover — never `title`.

## Adding one

What it replaces, and why a row of four wants no fifth, is [`surfaces.md`](surfaces.md). What is this file's:

1. Spend shape before colour, and **one fact per element** — two verdicts on one mark means the louder one wins
   and the other is invisible (`red` outranked an approval until 2026-08-26). One fact per HOVER too: the checks
   answer in the dot's tip and in no other, or two tips can disagree about one round.
2. Give it a row in `legend.js` rendering the element itself. Naming a colour in words is a second copy of it.
3. If it needs a colour nothing above covers, the meaning is probably one the board already has under another
   name. A fourteenth token is a decision, not a detail.

## Type and metrics

One family (`ui-sans-serif, system-ui`), one mono (`ui-monospace, SFMono-Regular, Menlo`) for anything a human
compares digit by digit: ages, aliases, token counts, report bodies.

| size | used for |
|---|---|
| 15px | the one `h1` |
| 14px | body |
| 13px | header controls, report meta |
| 12px | card meta, detail, buttons, chips |
| 11px | badges, status, `MR` age, tool-row buttons, legend heads |

Radii climb with the thing's size: `999px` for anything pill-shaped, `50%` for the dots, `6px` for a control
(button, input, tip, the drafts line), `8px` for a card or a toast, `10px` for the report dialog. Grid gap is
12px, card padding 10px, dialog padding 14px, meta gap 8px.

Shadow lifts a thing OFF the page and does nothing else — the report dialog, the log button, a toast, a tip —
with one exception that is not depth at all: the 2px `--ok` ring on a primary deploy button, where the mark had
nowhere else to go. **One animation exists**, `checks-pulse`, and it has a `prefers-reduced-motion` off-switch;
a second one needs a reason this file does not have yet.

## Both themes, offline, in one jar

Every one of the thirteen TOKENS is declared in both schemes — a token that exists in one is a hole in the
other. A *rule* inside the dark block is a different thing and there is exactly one, `button.primary`'s label
at `app.css:44-45`, because the light case is a literal too.

**No build step, no CDN, no external asset of any kind** — no web font, no icon set, no SVG sprite. Every mark
above is a border, a background or a character that ships in the jar.
