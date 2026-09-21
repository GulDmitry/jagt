# The protocol

[← AGENTS.md](../../AGENTS.md)

**Everything that crosses into jagt from outside is a message, and a message is validated once, at the door.**
`protocol/` in `core/` holds them: the wire record, its rules, and the types the rest of jagt works in. It
speaks the centre's vocabulary and nothing else, and the centre does not know it exists (`RingsTest`).

## No raw wire value travels inward

- A message arrives as the strings and maps it was written in. `violations(...)` judges it; `accepted(...)`
  returns it in jagt's own types (`protocol/Reported`) or nothing.
- **Nothing downstream re-reads the wire**: which status, what the round claims, which request it is about and
  what the human is shown are resolved once, here. A caller parsing a field again has found a leak.
- What a message cannot carry is not the protocol's to answer: a claim it makes about the worktree is measured
  where the worktree is (`service/HandBack`), and which status it may LAND on is `flow/FlowRules`.

## The arguments are read INTO the message, never field by field

`MessageTool` is the one path from the wire to a verb: the arguments are read into the message, judged, and
only then does the tool run. A field added to a message arrives without anyone remembering to read it — the
hand-written extraction it replaces is where a new one went missing. A field the message does not declare is
**ignored**: a CLI a version ahead must not have its call rejected over a word jagt has not learned.

**Required-ness is the message's answer too.** The transport parsed `required` back out of the schema it had
just rendered, answering one field at a time and first; it still does for tools read by hand.

## Two kinds of rule, one report

- **Field**: a value out of its enum, a missing required field, a link nobody can open.
- **Consistency**: two fields that cannot both hold — `reviewRequests` beside `reviewRequestUrl`, CI_POLLING
  with no request anywhere in the message, a request filed under a project the task does not have.
- **Every violation at once**, never first-failure: the sender is usually a model, and one error per call is
  one call per error. `startup/StartupValidation` refuses a bad install the same way.
- A violation names **the field and what was expected**, because the refusal is what the sender acts on.

## The shape is declared once, in the code

`protocol/Schema` renders what a caller is given out of the fields a message declares, so the JSON a CLI reads
and the rules that judge the answer cannot disagree. The enum comes from whatever enumerates it
(`TaskStatus.values()`), never from a list written beside it.

## A refusal is a correction, and the same request is sent again

- Every violation at once, each naming its field and what was expected, because the next thing a sender does is
  re-send the message corrected — not escalate it, not give up on it.
- **From a session**: the refusal comes back from the call; the brief tells the session to fix every line and
  call again, which is not a block and never a question for the human.
- **To a paid read**: jagt is the sender. `protocol/TicketRead` judges the answer and the violations ride into
  the next ask, because the identical question is what returns the identical answer.
- **Retries are bounded and end in a person** (`protocol/RetryPolicy`): three attempts for a paid read, spaced,
  under a budget, since every one is paid for. An exhausted policy answers with NO facts and never a guess:
  reaching the human is the caller's, and one that logs it and moves on is the bug this exists to stop. A round
  nobody could read taps the human ONCE (`AutoReviewScheduler`) — an unattended poll is the caller whose log
  nobody reads.

## Where the shapes are today

Every MCP tool is a message: `McpToolRegistry` has one way to declare one and it takes a message class, so a
tool that skips validation does not compile rather than failing a test. The four paid reads still carry
`--json-schema` blocks in `adapter/assistant/` — [`TODO.md`](../../TODO.md).
