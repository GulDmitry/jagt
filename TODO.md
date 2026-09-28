# jagt — TODO

## The jar runs only inside a clone of jagt (open)

`OrchestratorPaths.findRoot` walks up for `jagt.yml.dist` or `mcp_client.js`, and `AbstractAgentRuntime`
symlinks that bridge into each worktree from disk — both are repository files, so a released jar starts nowhere
else.

The plan: ship both as classpath resources, fall back to the launch directory as root, and write them there on
first run.

What to decide first: what `root` means once nothing marks it, since worktrees are cut beside it.

## An orchestrator that reads the code host and the tracker itself (concept, someday)

What jagt must promise before it may hold a token — where it is read from, what may act with it, and what the
board needs first, listening on loopback without auth and able to deploy. As it stands:
[`docs/rules/seams.md`](docs/rules/seams.md).

## Split one task's work into review requests a human can actually read (idea)

A large ticket lands as one request, and a request nobody can read is a request nobody reviews. A splitter
would send the same work out as several, cut at a configured size — around 300–400 changed lines each.

What to decide first: where a cut may fall (only a commit boundary leaves each request buildable), how they are
chained so the second targets the first, and what happens to comments a later cut has superseded.

## A flag for the tasks that matter, on the card (idea)

An inbox flag, top-right: the few tasks you must not lose sit in a board where every card reads equally urgent.

What to decide first: `card-top` already ends in the attention badge, so a flag there says what it replaces —
and no colour is free, `--danger` meaning broken and `--you` your move. Likely a shape, sorted first not tinted.

## Run the Master session against real work before anyone turns it on (idea)

It stays off until it has been driven by hand against live tasks: what it judged, where its verdict and yours
differed, what it cost per wake. [`docs/roadmap.md`](docs/roadmap.md) has the shape; the finished-task record
is what the comparison is read from.

What to decide first: what a trial run may touch — reading and writing a file only, no verb issued.

## The closed loop: a named event, a deterministic condition, and what it fires (concept, experimental)

Where this ends: a task reaching a status someone else's board reports — `Ready for Stage` in one install — is
`done`, its worktree dropped, nobody in the line. Two pieces exist: `notify/` fans out to every
`port/Notifier` an install carries, and adding a channel is one adapter class plus a config value.

The middle is missing: jagt has no named events, only the few places that happen to notify, and no routing —
every notifier gets everything. **The condition must be the machine's**: computed from facts jagt already holds
(`ReviewFacts.threads` empty, `Pipeline.RED`, a status reached), never a model's opinion that something looks
done. `done` is `HUMAN_ONLY` and withheld from the Master in every mode, so an event closing a task is the
report door judging a fact, never a verb pressed for you.

**Every trigger is a strategy, and so is the stage it fires** — intake, `done`, cleanup: an install with no
tracker configures a different fact rather than losing the stage.

What to decide first: where an event is declared so the compiler checks it rather than a config string; whether
routing is per channel, per event or both; and whether a strategy resolves by id per call, keeping `jagt.yml`
hot, or is wired at startup.
