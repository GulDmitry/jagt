# jagt — TODO

## A port does not declare what it can do (open)

`Viewer.supports(TAB_TITLES)`: nothing can ask whether the SELECTED adapter carries what a capability needs.
`AgentRuntime.lastSessionActivityMillis` answering 0 for "this CLI keeps no log" is the same hole with a magic
value in it.

A small interface change plus a check. Not worth doing before a consequence turns up that costs something.

## An orchestrator that reads the code host and the tracker itself (concept, someday)

Today every ticket and every review round is read by a model through the MCP servers of whoever runs jagt, and
jagt holds no credential. Doing those reads — and a `ship`'s write — itself would take the model out of the
path: one place to configure, one place to fail, every failure naming a key instead of a lost round.

It was built once (a `CodeHost` / `Tracker` seam over REST, removed 2026-08-24) and it never held: the wiring is
the easy half, and the question it never answered is the guarantee. jagt would become a secret store, while the
board listens on loopback WITHOUT auth and can already deploy — whoever reaches that port would act as the human
on the host. What jagt must promise before it may keep a token — where it is read from, what may act with it,
what the board needs first — is the work, not the adapters.

## What a session's hooks could also do (open, needs the owner)

Both are reachable now that a CLI's own hooks report into jagt, and neither is a reporting change — they change
what the session itself does, so they are not a sub-agent's to take.

**Re-brief a compacted session.** A `SessionStart` hook's stdout is added to the model's context, so jagt could
hand a session one line back after a compaction ("you are the sub-agent for ABC-42; re-read task_context.md").
Mechanically it is the hook line no longer throwing its own output away, plus the text to hand back.

- For: today a compaction silently drops the brief, and an agent that can no longer see the rules starts
  breaking them. It is the largest determinism hole left.
- Against: jagt would be writing model-facing text from a hook — a kind it has never had — and it costs tokens
  on every session start, including the ones that needed nothing.

**Refuse a shared-branch write from `PreToolUse`.** A hook may block a tool call with a reason the model reads.

- For: "never write to a shared branch" becomes enforced instead of asked for, in the one place that can see
  the command about to run.
- Against: `AGENTS.md` states the current guarantee is the detached upstream plus prompt rules, and a hook that
  enforces is one step from a git hook, which is banned outright. Where that safety lives is the owner's call.

## Small and decided, not done (open)

- **A sub-agent's token spend is readable for nothing.** The log a session keeps carries `usage` per turn, and a
  session now names that file itself, so the reading no longer has to find it. `UsageTracker` meters only the
  headless assistant — so the card shows no cost for the work that spends most.
