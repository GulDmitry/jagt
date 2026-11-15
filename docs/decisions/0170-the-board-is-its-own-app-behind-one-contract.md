# 0170 — The board is its own app, behind one hand-written contract

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)

## Status

Accepted 2026-11-15 by the owner's order: the front-end and the backend are two apps, with a contract between them.

## Context

- The board sat in the backend's resources; the backend named its files (the login's public paths, the catalogs it merged).
- The board is native ES modules with a vendored import map, no bare npm import: it needs no bundle to move.
- [0046](0046-the-board-renders-preact-components-with-no-build.md) served it from the jar's `static/`; that part is superseded.

## Decision

- The board lives at `board/` in the repository, apart from `orchestrator-backend/`.
- `contracts/openapi.yaml` and `contracts/board-events.schema.json` are the only coupling, written by hand.
- `ContractRoutesTest` fails when a mapping under `/api` (the agent's and `/mcp` excluded) is missing from the contract.
- The login's public paths are `x-login-page` and `x-public-paths` of the contract, never a list in Java.
- The jar carries a Gradle copy of `board/` served as opaque files; `orchestrator.board.dir` serves a folder instead, a relative one against the root.
- The board owns its catalogs; `<root>/i18n/` is served as plain files and merged by the board, so no language endpoint remains.
- `boardTest` stays in Gradle: the integration of both apps against a real backend.

## Rejected

- A bundle, TypeScript or a framework: nothing in the board needs them.
- Generating the contract from the controllers: the contract is what the board relies on, not what the code happens to map.
- A stub backend for `boardTest`: a stub proves the board against itself.
- Serving only from the jar: an edit to the board would need a rebuild.
- A trailing `/` appended to `board.dir` and `<root>/i18n`: Spring adds it, a folder made after start is served.
- `BoardLoginOnTomcatTest` reading `x-public-paths`: a glob cannot be requested; the test lists the fetches the login page makes.

## Reopen when

- The board needs an npm package with no ES module.
- The contract and the controllers drift once a test already pins the routes.
