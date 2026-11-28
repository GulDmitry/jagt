# 0271 — The design code takes a second rule page

[← decisions](README.md) · rule: [`design`](../rules/design.md)
on [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-28. Raises `rule-docs` from 9 to 10.

## Context

- `design.md` stood at its 100-line budget; describing each `app.css` class and token once adds about 25 table rows,
  and the board's code design 12 lines.
- Compressed, the palette, metrics and code design fill 77 lines and the marks with the other elements 76: no compression
  keeps every fact in one page.

## Decision

Why: AGENTS.md "Writing" splits a page past its budget one level deep rather than dropping a fact.

- `docs/rules/design-elements.md` holds the marks and every other class; `design.md` links it.
- `rule-docs` in `fitness.yml` is 10.

## Rejected

- A page per element group: each split counts against the ceiling for no fact gained.

## Reopen when

- The design code fits one page again, or a third design page is asked for.
