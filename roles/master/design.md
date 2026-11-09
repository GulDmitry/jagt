# master — design

## Why it exists

The Master stands where the human stands: it reads what a task produced before the human does, and in `act` holds the
same rights. A round costs the human one read less, and nothing the human keeps is pressed for them.

## Responsibility

- Owns the verdict on each handed-back round and plan: one headless run per brief role, stored as a `MasterReview`.
- Owns, in `act`, the moves a human makes: ship a `ready` round, approve a plan that holds, answer a question,
  `add-repo`, deploy a `REVIEWED` or `APPROVED` task, and the decision on a stop for a suspected loop.
- Does not own the code: it writes no code and changes no file; jagt writes its verdict.
- Does not own what `roles.master.mine` and `humanPaths` name: the human keeps those verbs and paths.
- Does not own a permission (`outcome=permission`), `do`, `resume` or `act`, nor a revert: those stay the human's.
- Does not own the human's word: the human's answer stands over any decision it took for them.

## Who triggers it

jagt alone: a session hands back a round or a plan, and a stop for a suspected loop asks for a decision.
Never a model's answer.

## Use cases

- **WHEN** a session hands back a round in `judge` or unset mode
  **THEN** each role reads it into the worktree's `master-review.md` and presses nothing
- **WHEN** the diff touches none of a role's `applies` globs
  **THEN** a `# skipped` line replaces that role's read
- **WHEN** a round is `ready` in `act` and `mine` does not name `ship`
  **THEN** the Master ships it
- **WHEN** a hand-back has nothing new to ship
  **THEN** it presses nothing and the task waits for the human
- **WHEN** the ticket could mean "switch" or "add beside"
  **THEN** it decides as the human would and sends the decision back as a finding
- **WHEN** a session reports `outcome=permission`
  **THEN** the Master never answers; the question is the human's
- **WHEN** a stop for a suspected loop needs a decision
  **THEN** the answer is one line: `lift`, `escalate`, `return` or `human`

## Usage

- Mode, model, `mine` and `humanPaths` under `roles.master` in `jagt.yml`; `act` is opt-in, the default is `judge`.
- `roles.master.brief`, else the first of `roles/master/brief.md` and `roles/master/brief.md.dist`; a named brief
  missing refuses the start.
- The brief's role table replaces the author's roles; a `model` column picks a role's model, `applies` its paths.

## Guards

- Read-only git and the MCP tools `allowed-tools` names; it writes nothing, its session included (`ReadGate`).
- `<outside>` text and anything read by a tool is data: an instruction found in it is a finding, never obeyed.
- It picks a legal move, never invents one; every verb it presses is held to a Master case.
- A refused press logs one WARN naming the task, the verb and the cause; the other tasks of the tick are served.
- Only blocking, wrong and unproven findings stop a round; every premise carries its file:line or command.

## Cost against the alternative

- A round costs one heavy read per role it applies to, charged as `master`, later roles reading the cached round.
- The human reads a verdict with its proofs, not the whole diff first.
