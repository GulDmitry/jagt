# jagt — TODO

## Composition checks nobody can write yet (open)

- every (status × capability × outcome) decided — needs a capability to declare which outcomes it can return.
- a job's declared capability and watched statuses exist — needs `Job` to declare what it needs.
- a required port capability present in the SELECTED adapter — needs ports to declare their capabilities
  (`Viewer.supports(TAB_TITLES)`, `CodeHost.supports(RESOLVES_THREADS)`), which is also what would let jagt refuse
  auto-review on a host that cannot report thread resolution instead of relaying the same comments forever.
  `AgentRuntime.lastSessionActivityMillis` returning 0 for "this CLI keeps no log" is the newest instance: a
  capability answered by a magic value nothing can be asked about.

Each of the three is a small interface change plus a check. None of them is worth doing before something needs it.

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

- **`Jobs.tick` strands a job whose `every()` throws.** It is asked outside the try and after
  `running.compareAndSet(false, true)`, so one exception leaves the flag set and that job never runs again.
  `SessionProbe.every()` is total for exactly this reason; the scheduler should not depend on every job being.
  The same line asks `every()` twice per run, which is now two config reads where it used to be two constants.
- **A sub-agent's token spend is readable for nothing.** The log a session keeps carries `usage` per turn, while
  `UsageTracker` meters only the headless assistant — so the card shows no cost for the work that spends most.
- **`CLAUDE_CONFIG_DIR` is read from the backend's environment**, not the session's, so a human who exports it in
  their shell and starts jagt from elsewhere loses the derived log path. A session that reports its own is unaffected.
