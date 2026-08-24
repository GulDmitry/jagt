# jagt — TODO

## A port does not declare what it can do (open)

`Viewer.supports(TAB_TITLES)`, `CodeHost.supports(RESOLVES_THREADS)`: nothing can ask whether the SELECTED adapter
carries what a capability needs. `AgentRuntime.lastSessionActivityMillis` answering 0 for "this CLI keeps no log"
is the same hole with a magic value in it. The one consequence that costs something today is auto-review on a host
that cannot report thread resolution: jagt relays the same comments round after round instead of refusing.

A small interface change plus a check. Not worth doing before a second consequence turns up.

## Move the code host's credentials into the orchestrator (open, conceptual)

Today jagt holds a token only when `orchestrator.code-host.*` / `tracker.*` are set; everything else reaches a
host through the AGENT's own MCP session, i.e. the human's credentials. Making jagt the credential holder is what
would let it do the outside reads and writes itself, deterministically, with no model in the path.

- For: `ship`, a review round and a resume stop depending on an agent session and on a paid read. One place to
  configure, one place to fail, and every failure names a key instead of a lost round.
- Against: jagt becomes a secret store. The board listens on loopback WITHOUT auth and can already deploy — with
  credentials behind it, whoever reaches that port acts as the human on the host. `docs/rules/components.md`
  states the current property plainly ("with neither configured, the backend holds no credential at all"), and
  that sentence is what would have to go.
- So the question is not the wiring (it exists) but the guarantee: what jagt must promise before it is allowed to
  keep a token — where it is read from, what may act with it, and what the board needs before it may.

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
