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

## Make the small reads thin (open)

A read that fetches one ticket, or one merge request, loads whatever MCP the human's config carries — today ~35
servers, most of them irrelevant, two of them named something with "gitlab" in it. That is what makes the
cheapest call in jagt cost ~25k tokens of context before it reads anything, and what lets a model pick a server
that cannot answer.

Thin means per PURPOSE: one or two servers named in the local config — a ticket read loads the tracker, a request
read loads the code host, neither loads the rest. `orchestrator.assistant.mcp-config` already pins a server
list, but one list for every call; the open part is a list per kind of read (`AssistantCallKind` already names
the kinds) and what a kind with no list of its own falls back to.
