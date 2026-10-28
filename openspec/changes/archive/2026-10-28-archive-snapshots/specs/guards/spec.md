## ADDED Requirements

### Requirement: The finished log packs its older half
`finished.jsonl` past 10 MB SHALL pack its older half beside it as `finished-<stamp>.jsonl.gz`; the newer half stays where
every reader looks.

#### Scenario: The finished log passes its ceiling
- **WHEN** `done` appends a line past 10 MB
- **THEN** the older half is packed beside it and the `finished` report names the archive
- **AND** an intake still sees the newer half's ids

### Requirement: A done leaves one snapshot
Each `done` SHALL write `archive/<yyMMdd-HHmmss>-<task>/` beside `state.json`: the task's documents and `finished.json`,
the pretty-printed finished record, which holds `usage`, `agentUsage` and the paths of the session logs as `transcripts`.
The logs are not copied. A document past 10 MB SHALL NOT be copied; `<name>.not-kept` names its size.
A failure writing a snapshot is logged and never refuses `done`.

#### Scenario: A task done twice in one day
- **WHEN** ABC-42 is done at 09:00 and again at 15:00 the same day
- **THEN** `archive/` holds `<day>-090000-ABC-42` and `<day>-150000-ABC-42`, each with its own documents

#### Scenario: A finished record with its budgets
- **WHEN** a task with its own usage and agent-session usage is done
- **THEN** its snapshot holds its documents and a `finished.json` carrying its id, `usage`, `agentUsage` and `transcripts`

#### Scenario: A runaway document
- **WHEN** a finished task's `plan.md` is 12 MB
- **THEN** its snapshot holds `plan.md.not-kept` instead

### Requirement: The archive is bounded by bytes
After each snapshot, while `archive/` holds more than `archive.maxMegabytes` of `jagt.yml` (default 20), the oldest entry
by name SHALL be deleted, an older `archive-*.zip` first. The snapshot just written is never deleted.

#### Scenario: The archive passes its budget
- **WHEN** a snapshot takes `archive/` past `archive.maxMegabytes`
- **THEN** the oldest entries are deleted until it fits, and the newest snapshot stays

## REMOVED Requirements

### Requirement: A record store packs its older half
**Reason**: Split: the finished log keeps its packing; the artifacts store is now a byte-bounded archive of snapshots.
**Migration**: `finished.jsonl` packing is "The finished log packs its older half"; `artifacts/` is `archive/`, read by nothing.
