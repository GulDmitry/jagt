---
paths:
  - "**/src/test/**"
  - "**/src/e2e/**"
  - "**/src/boardTest/**"
  - "**/src/linuxTest/**"
  - "orchestrator-backend/scripts/**"
  - "**/.github/workflows/**"
  - ".gitlab-ci.yml"
---

Load `sob-ai:unit-testing` before touching any test file. Every fixed bug gets a regression test, verified RED.

Full rules for testing etiquette: **`docs/rules/testing.md`** — read it before changing behaviour here.
