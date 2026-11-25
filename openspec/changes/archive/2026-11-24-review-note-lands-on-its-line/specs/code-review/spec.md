## MODIFIED Requirements

### Requirement: A note is posted once and says who wrote it
`NotePoster` SHALL be the one caller of `RequestNotes.post`. Each body SHALL open with the constant AI line asking for a thumbs up or down.
Each note SHALL carry a hidden key, a digest of request, head, file, anchor and kind, read back from the host before every post.
A finding naming a file and a line SHALL be posted on that line.
A finding with no diff line, or whose line the host refuses, SHALL post no note and one WARN SHALL name the cause.
A finding that an open thread of the role's own already says SHALL post nothing.
jagt SHALL never approve, merge, close or push through the role.

#### Scenario: A restart between a post and its ledger write
- **WHEN** jagt restarts between a post and its ledger write
- **THEN** the next run finds the key on the host and posts nothing

#### Scenario: The request moved on
- **WHEN** the request is closed, merged or its head moved since the read
- **THEN** the judgement is dropped unposted

#### Scenario: Another person's request, posting off
- **WHEN** `post.others` is off and the request is another person's
- **THEN** it gets no note and no model call

#### Scenario: No approval
- **WHEN** any run ends
- **THEN** no approval was sent, as `RequestNotes` has no approve method

#### Scenario: A finding names a file and a line
- **WHEN** a finding quotes a line of a file the diff changes
- **THEN** the note is posted on that line, its position sent as JSON where the host needs it

#### Scenario: The host refuses the line
- **WHEN** the host refuses a note's line
- **THEN** no note is posted and one WARN names the cause

#### Scenario: A finding has no diff line
- **WHEN** a finding names no line of the diff
- **THEN** no note is posted and one WARN names the cause

#### Scenario: A re-review after a new push
- **WHEN** a finding is what an open thread of the role's own already says
- **THEN** nothing new is posted
