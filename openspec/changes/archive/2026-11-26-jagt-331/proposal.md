## Why

The CSP hashed the jar's import maps while `orchestrator.board.dir` serves a folder in the jar's place, so a folder whose import map differs got a header admitting the wrong script.

## What Changes

- The CSP hashes the board pages and the editor import map from the folder the board is served from.
