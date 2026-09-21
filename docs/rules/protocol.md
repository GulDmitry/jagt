# The protocol

[← AGENTS.md](../../AGENTS.md)

**Everything that crosses into jagt from outside is a message, and a message is validated once, at the door.**
`protocol/` in `core/` holds them: the wire record, its rules, and the types the rest of jagt works in. It
speaks the centre's vocabulary and nothing else, and the centre does not know it exists (`RingsTest`).

## No raw wire value travels inward

- A message arrives as the strings and maps it was written in. `violations(...)` judges it; `accepted(...)`
  returns it in jagt's own types (`protocol/Reported`) or nothing.
- **Nothing downstream re-reads the wire.** Which status, what the round claims, which request it is about and
  what the human is shown are resolved in `protocol/`, once. A caller that parses a field again has found a
  leak, not a convenience.
- What a message cannot carry is not the protocol's to answer: a claim it makes about the worktree is measured
  where the worktree is (`service/HandBack`), and which status it may LAND on is `flow/FlowRules`.

## The arguments are read INTO the message, never field by field

`MessageTool` is the one path from the wire to a verb: the arguments are deserialized into the message, judged,
and only then does the tool run. A field added to a message arrives without anyone remembering to read it, which
the hand-written extraction it replaces is exactly where a new field went missing. A field the message does not
declare is **ignored**: a CLI one version ahead must not have its call rejected over a word jagt has not learned.

**Required-ness is the message's answer too.** The transport used to check presence by parsing the schema it had
just been handed, one field at a time and before the rules ran. It still does that for tools read by hand.

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
(`TaskStatus.values()`), never from a list written out beside it. A schema written by hand next to the code that
reads the fields is the duplication this replaces.

## A refusal is a correction, and the same request is sent again

- Every violation at once, each naming its field and what was expected, because the next thing a sender does is
  re-send the message corrected — not escalate it, not give up on it.
- **From a session**: the refusal comes back from the call; the brief tells the session to fix every line and
  call again, which is not a block and never a question for the human.
- **To a paid read**: jagt is the sender. `protocol/TicketRead` judges the answer and the violations ride into
  the next ask, because the identical question is what returns the identical answer.
- **Retries are bounded and end in a person** (`protocol/RetryPolicy`): three attempts for a paid read, spaced,
  under a budget, since every one of them is paid for. An exhausted policy answers with NO facts and never a
  guess — reaching the human is then the caller's, and a caller that logs it and moves on is the bug this
  exists to stop. A round nobody could read taps the human ONCE per round (`AutoReviewScheduler`), because an
  unattended poll is the one caller with nobody watching its log.

## Where the shapes are today

`protocol/AgentStatusMessage` is the first message gathered. The others are still scattered — the MCP tool
declarations are inline schema strings in `surface/mcp/tools/`, and the paid reads carry `--json-schema` blocks
in `adapter/assistant/`. Gathering them is [`TODO.md`](../../TODO.md).
