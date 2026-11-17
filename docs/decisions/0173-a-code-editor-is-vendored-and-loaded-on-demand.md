# 0173 — The code viewer's library is vendored, past 50 KB, and loaded on demand

[← decisions](README.md) · rule: [`design.md`](../rules/design.md), [`board`](../../openspec/specs/board/spec.md)

## Status

Accepted 2026-11-16; reopens [0046](0046-the-board-renders-preact-components-with-no-build.md)'s "vendor files past 50 KB".

## Context

- A read-only code viewer with highlighting, search and a side-by-side diff needs an editor library.
- CodeMirror 6 ships ES modules and its packages import each other by bare name.
- Measured 2026-11-16, npm registry latest, unminified `dist/index.js`, raw and `gzip -9`:

| part | files | raw | gzip |
|------|-------|-----|------|
| core: state, view, language, streamparser, search, merge, lezer common/highlight/lr, style-mod, w3c-keyname, crelt, find-cluster-break | 13 | 1043 KB | 252 KB |
| grammars java, javascript, json, yaml, markdown, css, html, python, their lezer parsers, autocomplete | 17 | 505 KB | 154 KB |
| all | 30 | 1548 KB | 407 KB |

## Decision

- The files sit in `board/vendor/codemirror/` byte for byte as the registry served them, named `<scope>-<package>.js`.
- `vendor/codemirror/importmap.json` maps every bare specifier to its file; the loader appends it as an
  import map before its first `import()`.
- The map is no inline `<script>` in `index.html`: the 224 KB budget has no room for its 4 KB.
- The files load on demand by `import()`: the first paint fetches none, as with mermaid.
- `BoardAssetsTest` pins each file's SHA-256 and leaves the folder out of the 224 KB budget; the number stays.
- The viewer's own code sits in `board/viewer/`, `import()`ed when the Code tab first opens.
- `BoardAssetsTest` leaves `board/viewer/` and `vendor/hooks.mjs`, which only the viewer imports, out of the 224 KB: the eager part has no room.
- `@codemirror/autocomplete` is vendored because `lang-javascript` and `lang-markdown` import it.
- `commands` and `lint` are not vendored: no vendored file imports them.
- Three LICENSE files, one per holder text: codemirror, lezer, lezer-json.
- Bumping a package replaces its file and its pin together.

## Rejected

- A bundle: a build step and a lock file, against [0170](0170-the-board-is-its-own-app-behind-one-contract.md).
- Minifying: the files are no longer what the registry served.
- A CDN: AGENTS.md forbids external assets.
- Loading with the page: 407 KB gzip on every first paint for a view most sessions never open.

## Reopen when

- The viewer loads slowly on the measured on-demand path.
- A vendored file imports a package missing from the map.
- A package ships no ES module.
