# Stand engines
[← Features](../features.md) · Each line names one thing the stand does on an engine and links to the requirement that defines it.

## Stand engines · [spec](../../openspec/specs/stand-engines/spec.md)
- **Engine by name** — `standTest -Pengine=host|compose|kind` starts jagt as a jar, under compose or in a kind cluster.
  [spec](../../openspec/specs/stand-engines/spec.md#requirement-the-stand-runs-on-an-engine-chosen-by-name)
- **Server scenarios** — Start, login, a task to review, the agent door and `/state` p95 on every engine.
  [spec](../../openspec/specs/stand-engines/spec.md#requirement-the-server-scenarios-hold-what-the-container-suite-held)
- **Restart events** — The JVM killed, the container or pod replaced, a new image on the old volume; the session keeps its conversation.
  [spec](../../openspec/specs/stand-engines/spec.md#requirement-a-restart-is-an-event-of-the-stand)
