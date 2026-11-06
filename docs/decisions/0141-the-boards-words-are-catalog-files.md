# 0141 — The board's words are catalog files

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)

## Status

Accepted 2026-11-06 by the owner's order: the board speaks any language by adding one file.

## Context

- The board's text sat in its scripts and page; a second language meant editing code.
- The board has a 160 KB budget for scripts, styles and pages (`BoardAssetsTest`); a catalog is data and stands outside it.
- jagt runs from an installed jar whose users cannot rebuild it.

## Decision

- One flat JSON per language, `{"key": "text"}`, placeholders `{name}`, named by its BCP 47 code: `en.json`, `pl.json`, `pt-BR.json`.
- `en.json` holds every key and is the fallback: a key another catalog lacks reads English.
- The languages offered are the files present, in the jar under `static/i18n/` and in `<root>/i18n/`.
- A file in the root overrides or adds; no list, registration or code names a language.
- `BoardLanguages` serves the codes and each merged catalog; a language's own name is its `language.name` key.
- Only the board's own UI text is translated; logs, specs, code, backend reports, verb words and times stay English and UTC.
- The choice is kept per browser in `localStorage`, beside the theme, on the gear; the page loads its catalog before it renders.
- The flat shape is what i18next reads, so a library can replace `core/i18n.js` without touching a catalog.

## Rejected

- A vendored i18n library: its weight takes the 160 KB budget the board keeps, and `t(key, values)` is all the board uses.
- A language list in code or `jagt.yml`: a second place to forget when a file is added.
- Catalogs in the jar only: a user of the installed jar could not add a language.
- Translating by browser locale alone: a human cannot then choose a language the browser does not offer.

## Reopen when

- The board needs plurals or gendered forms a flat `{name}` placeholder cannot say.
- `core/i18n.js` outgrows a hundred lines.
