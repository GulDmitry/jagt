---
paths:
  - "**/dev/jagt/orchestrator/protocol/**"
  - "**/surface/mcp/MessageTool.java"
---

Everything crossing into jagt is a message, validated once, at the door. Nothing downstream re-reads the wire.

Full rules for messages, reads and schemas: **[`docs/rules/protocol.md`](../../docs/rules/protocol.md)** —
read it before changing behaviour here.
