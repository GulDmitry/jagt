# 0046 — The board renders Preact components, with no build

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md), [`design.md`](../rules/design.md)

## Status

Accepted 2026-10-16.

## Context

- The board was hand-built DOM: `ui/render` diffed cards with `isEqualNode` and re-found focus and tips by hand.
- The redesign ([board-redesign](../research/board-redesign.md)) adds a list, a task pane, tabs and panels.
  Each one needs a state, and a place where every state can be seen.
- AGENTS keeps the board free of a build step, a CDN and any external asset.
- On 2026-10-16: Preact 11.0.1 + hooks + htm 3.1.1 weigh 16.8 KB minified; `static/` weighs 104 KB.

## Decision

- Components are Preact, written with `htm` tagged templates; the three files sit in `static/vendor/` byte for byte
  as the npm registry served them, resolved by an import map. `BoardAssetsTest` pins their SHA-256.
- A component takes everything it shows as props, and reads no module state.
- `styleguide.html` renders every component in every state from `styleguide/` fixtures; `boardTest` loads it.
- What the human presses is logged, a control and a status per line, under the ceiling in `guards`.
- Migration is by component, with `boardTest` green at each step; the first port is the card.

## Rejected

- React + Vite + Storybook: Node, npm and a lock file in a Java build, for one page served from a jar.
- Lit or web components: a second component model beside the one the redesign needs.
- Keeping hand-built DOM: the task pane and tabs would each need their own diffing and focus code.

## Reopen when

- A page needs a library that ships no ES module, or the vendor files grow past 50 KB.
- Preact's next major breaks htm, or the import map stops loading in a supported browser.
