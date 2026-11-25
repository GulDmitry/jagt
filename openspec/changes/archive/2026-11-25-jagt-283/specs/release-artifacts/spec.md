## MODIFIED Requirements

### Requirement: A running jagt keeps a newer release verified beside it
Run from `<root>/versions/<v>/`, the `releasecheck` job SHALL hourly keep the feed's newer release under `versions/` once its jar matches its sha256.
The feed is `orchestrator.releaseFeed.kind` (`github` default, `none`) at `orchestrator.releaseFeed.url`.
`current` is never changed, and every network call has a timeout.
#### Scenario: A newer release is kept verified beside the running one
- **WHEN** the feed names a newer version and its jar matches the published sha256
- **THEN** `versions/<version>/jagt.jar` exists beside the running version and `current` is unchanged
#### Scenario: A mismatch is discarded
- **WHEN** the downloaded jar does not match its published sha256
- **THEN** nothing is kept under `versions/` and no staging folder remains
#### Scenario: Not an installed layout
- **WHEN** jagt runs from a jar outside `<root>/versions/<v>/`
- **THEN** the feed is not asked and nothing is downloaded
