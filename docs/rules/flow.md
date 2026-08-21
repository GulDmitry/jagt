# The flow machine

[← AGENTS.md](../../AGENTS.md)

## The flow machine

`flow/FlowRules` is the whole life of a task in one file: which statuses allow which action (the guard reads
`flow/Facts` — an open request, and a liveness probe the projection passes as "no" because it costs a process
spawn per row), and what each outcome of that action leads to.

**Door one** is `flow/FlowEngine.run`: check the rules, run the `capability/TaskCapability` registered for the
action, write the status the table gives for its `flow/Outcome`.

**Door two** is `flow/FlowReports`: a status the task itself reports — its agent over MCP, or a round jagt read
for it. Refused unless `FlowRules.refusedReport` allows it, **and it owns the reason**, which is what the agent
acts on. That is what stops a task talking itself onto a shared branch, out of one, or closed.

A status a **human** owns is not refused but **held**: `FlowRules.reported` keeps a REVERTED task where it is
and records the line. An agent's protocol is to keep saying what it is doing, so a status it cannot report is a
session whose every call errors — while a status that *follows* it took the revert off the record and laundered
the CI_POLLING guard through the `IN_PROGRESS` it had just claimed.

**Nothing below `flow/` names a status.** A capability does the work and reports OK / RELAYED / CONFLICT /
PARTIAL / GONE plus the sentence and the stamp, so the same work can be reached from several statuses without
every doer learning the machine.

`withStatus` therefore appears in `flow/` and in the record that implements it, **nowhere else** — that is
greppable, and it is the invariant.

PARTIAL is the one outcome that **refuses**: stamped on the task first and thrown second, because a shared
branch holding half a change must be recorded, not merely complained about.

The table stays Java rather than config: every status and action in it is checked by the compiler.
