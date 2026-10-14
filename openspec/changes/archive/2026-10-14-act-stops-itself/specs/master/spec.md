## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round, start a plan that holds and answer a question. It SHALL
deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`); `master.mine` SHALL keep named steps
(`task/MasterRight`) the human's. Closing SHALL stay the human's: the Master's `done` is refused. Three tasks in a
row it moved ending silent, in a deploy conflict or reverted SHALL stop `act` (`MasterBrake`); a deploy resets the
count.

#### Scenario: Three tasks it moved end badly in a row
- **WHEN** the third one is stamped silent, conflicts on deploy or is reverted
- **THEN** it only judges, across restarts, until you press the header's `act stopped`

## REMOVED Requirements

### Requirement: Every Master step is seen in one window
**Reason**: moved to `unattended-work`.
**Migration**: none; the behaviour is unchanged.
