# 0184 — The board answers with a Content-Security-Policy of its own origin

[← decisions](README.md) · on [0173](0173-a-code-editor-is-vendored-and-loaded-on-demand.md) ·
rule: [`board-security`](../../openspec/specs/board-security/spec.md)

## Status

Accepted 2026-11-16 by the owner's order: gaps before features, from the JAGT-121 baseline audit.

## Context

- The board loads nothing from outside the jar ([`design`](../rules/design.md)), so a policy of its own origin costs it nothing.
- Two inline scripts stood in pages (theme before first paint, the login page's i18n) and three import maps
  (`index.html`, `styleguide.html`, the editor's, appended by script). An import map is script: `script-src 'self'` blocks it.
- The vendored editor (style-mod) and diagram library create `<style>` elements; a nonce reaches neither without editing
  files that stay byte for byte as the registry served them (0173).

## Decision

- `default-src 'self'`; `script-src 'self'` plus the SHA-256 of each import map, computed at start from the page and the editor's
  `importmap.json` by `BoardCsp`: no second copy of a hash, and an edited map needs no edited policy.
- The two inline scripts became `theme.js` and `login.js`; the login form's inline style became a class.
- `style-src 'self' 'unsafe-inline'`: inline style runs no code, the vendored files inject it.
- `img-src 'self' data:`: the favicon's status dot is a `data:` image.
- `object-src 'none'`, `base-uri` and `form-action 'self'`, `frame-ancestors 'none'`.
- Spring Security's `headers().contentSecurityPolicy` writes it on both chains (`OpenBoard`, `GuardedBoard`).
- A new spec capability, `board-security`, holds this and the board's other protections of a login (`specCapabilities` 45 to 46, after 0182 took 45):
  none of the 44 is about a hostile page or a guessed password.

## Rejected

- Hash of each map hard-coded in Java: a second home for a fact the pages already hold.
- Rewriting the vendored files' bare imports to relative paths: breaks 0173's byte-for-byte pin.
- A nonce per response: the static files are served as they are, with no template step.
- No inline-style allowance: breaks the editor's and the diagrams' styling.

## Reopen when

- The editor or diagram library takes a nonce, or an import map can be external: `style-src` and the hashes go.
- A board served from `orchestrator.board.dir` carries an import map the jar's pages do not: its hash is not in the policy.

Sources checked 2026-11-16: MDN "Content-Security-Policy", `script-src` (import maps need `unsafe-inline` or a hash) and
`style-src`; Spring Security reference, "Content Security Policy"; `style-mod` source in `board/vendor/codemirror/`.
