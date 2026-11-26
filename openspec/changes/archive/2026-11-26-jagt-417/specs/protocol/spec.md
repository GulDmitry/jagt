## MODIFIED Requirements

### Requirement: The protocol lives in core
`protocol/` in `core/` SHALL hold the wire record, its rules and the types the rest of jagt works in. It speaks the centre's vocabulary; the centre
does not know it (`RingsFitness`). What a message cannot carry is not the protocol's: a worktree claim is measured where the worktree is
(`service/HandBack`), and which status it may land on is `flow/FlowRules`'s ([task-flow](../task-flow/spec.md)).

#### Scenario: A round claims no diff
- **WHEN** a message says nothing changed
- **THEN** `service/HandBack` measures the worktree

### Requirement: Arguments are read into the message
`MessageTool` SHALL be the one path from the wire to a verb: arguments are read into the message and judged before the tool runs. A field the message
does not declare is ignored, so a CLI a version ahead is not rejected; one misspelling a field the call left out is refused. A blank field reads as
absent and a padded one is stripped, the same for every message (`protocol/Wire`).

#### Scenario: An unknown, a misspelled or a blank field
- **WHEN** a call carries an undeclared field
- **THEN** it is ignored and the rest judged
- **WHEN** a call sends `task_id` and no `taskId`
- **THEN** it is refused naming `taskId`, never run on the caller's own task
- **WHEN** a field is blank or padded with spaces
- **THEN** the blank one reads absent and the padded one is stripped

### Requirement: A paid read is judged and asked again
For a paid read jagt is the sender: `protocol/TicketRead`, `RoundRead` and `RowStatusRead` SHALL judge the answer, and its violations ride into
the next ask: the identical question returns the identical answer. A verdict, press and row status are types, never Strings.

#### Scenario: A false "does not exist"
- **WHEN** the read says a ticket that plainly exists does not
- **THEN** it is asked again, up to three times, each attempt told what the last got wrong

#### Scenario: The wrong ticket
- **WHEN** the read answers about a different key, or with no key or link
- **THEN** no task opens, and the message says why

#### Scenario: Prose instead of the object
- **WHEN** a paid call answers in prose, not the schema's object
- **THEN** it is asked once more at once, both calls billed; a failed call is not

#### Scenario: A word outside the vocabulary
- **WHEN** a review answers a verdict, or an acceptance row a status, that names none of the declared words
- **THEN** it is asked once more, told the field, then failed with its cause on the card; an undeclared field is ignored
