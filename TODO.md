# jagt — TODO

## A port does not declare what it can do (open)

An interface under `core/port/` cannot say WHICH of its methods the selected adapter actually honours, so a
caller either assumes it can or reads a return value that means two things. Two live instances:

- **`TerminalDriver.reveal`** promises only the window: *"a driver that cannot select a TAB raises the window and
  returns true"*. `KittyTerminalDriver` selects the task's own tab; `WarpTerminalDriver` raises the window. Both
  answer true, so `focus` reports the same success whether the human is now looking at their task or at whatever
  tab that window was already showing.
- **`AgentRuntime.lastSessionActivityMillis`** answers 0 for "this runtime keeps no record of itself"
  (`AbstractAgentRuntime`). Zero is also what a failed read answers, and the two want opposite handling: no
  record means lean on the other signs and never call it silence, while a failed read means naming what failed.
  That is [a read that failed is never an answer](docs/rules/seams.md) one layer down, in a magic value instead
  of a prompt.

The shape of the fix: something on the port to declare with — `supports(…)`, or a capability set — and ONE check
where a capability is chosen, so a missing one is refused or reworded rather than silently degraded.

Not worth doing before a consequence turns up that costs something.

## An orchestrator that reads the code host and the tracker itself (concept, someday)

Today every ticket and every review round is read by a model through the MCP servers of whoever runs jagt, and
jagt holds no credential. Doing those reads — and a `ship`'s write — itself would take the model out of the
path: one place to configure, one place to fail, every failure naming a key instead of a lost round.

It was built once (a `CodeHost` / `Tracker` seam over REST, removed 2026-08-24) and it never held: the wiring is
the easy half, and the question it never answered is the guarantee. jagt would become a secret store, while the
board listens on loopback WITHOUT auth and can already deploy — whoever reaches that port would act as the human
on the host. What jagt must promise before it may keep a token — where it is read from, what may act with it,
what the board needs first — is the work, not the adapters.

## Make the small reads thin (open)

A read that fetches one ticket, or one merge request, loads whatever MCP the human's config carries — today ~35
servers, most of them irrelevant, two of them named something with "gitlab" in it. That is what makes the
cheapest call in jagt cost ~25k tokens of context before it reads anything, and what lets a model pick a server
that cannot answer.

Thin means per PURPOSE: one or two servers named in the local config — a ticket read loads the tracker, a request
read loads the code host, neither loads the rest. `orchestrator.assistant.mcp-config` already pins a server
list, but one list for every call; the open part is a list per kind of read (`AssistantCallKind` already names
the kinds) and what a kind with no list of its own falls back to.

## Split one task's work into review requests a human can actually read (idea)

A large ticket lands as one branch and one request, and a request nobody can read is a request nobody reviews.
The idea is a splitter: the same work goes out as several requests, cut at a configured size — around 300–400
changed lines each, counting added plus removed, which is the number a reviewer's attention actually tracks.

The size is configuration, not a constant. What the splitter needs deciding first: where a cut is allowed to
fall (a commit boundary is the only one that leaves each request buildable), how the requests are chained so
the second targets the first rather than the base branch, and what happens to a round of comments on a request
that a later cut has already superseded.
