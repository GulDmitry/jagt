# The flow machine

[← AGENTS.md](../../AGENTS.md)

`flow/FlowRules` is the whole life of a task in one file: which statuses allow which action, and what each
outcome of that action leads to. The guard reads `flow/Facts` — an open request, and a liveness probe the
projection passes as "no" because it costs a process spawn per row.

The twelve statuses: `NEW` nothing reported yet · `IN_PROGRESS` the agent is working · `REVIEW_PENDING` back
with the human · `SHIPPING` a push in flight · `CI_POLLING` a round is open · `CI_FAILED` checks red ·
`REVIEWED` nothing unresolved and CI green, not approved · `APPROVED` a human approved the request ·
`DEPLOY_CONFLICT` a human resolves it in the deploy worktree · `DEPLOYED` live on the deploy branch ·
`REVERTED` the deploy is out, branch and commits surviving · `DONE` closed.

**Door one** is `flow/FlowEngine.run`: check the rules, run the `port/TaskCapability` registered for the
action, write the status the table gives for its `flow/Outcome`.

**Door two** is `flow/FlowReports`: a status the task itself reports — its agent over MCP, or a round jagt read
for it. Refused unless `FlowRules.refusedReport` allows it, **and it owns the reason**, which is what the agent
acts on. That stops a task talking itself onto a shared branch, out of one, or closed.

A status a **human** owns is not refused but **held**: `FlowRules.reported` keeps a REVERTED task where it is
and records the line. Refusing it instead makes every call of that session error; letting the *following*
status through takes the revert off the record and launders the CI_POLLING guard through `IN_PROGRESS`.

The same hold covers a **verdict past a deploy**: REVIEWED or APPROVED read off the request leaves a DEPLOYED,
DEPLOY_CONFLICT or DONE task where it is, or an unattended poll drags shipped work back into the phase asking
for an approval.

**Nothing below `flow/` DECIDES a status.** A capability reports OK / RELAYED / CONFLICT / PARTIAL / GONE plus
the sentence and the stamp, so the same work is reachable from several statuses without every doer learning
the machine. A report's own sentence may still tell a human which status was reached — `DeployService` says
`; DEPLOYED` / `; REVERTED` — and nothing parses those words.

`withStatus` therefore appears in `flow/` and in the record that implements it, **nowhere else** — greppable,
and therefore the invariant.

PARTIAL is the one outcome that **refuses**: stamped on the task first and thrown second, because a shared
branch holding half a change must be recorded, not merely complained about.

The table stays Java rather than config: every status and action in it is checked by the compiler.
