## ADDED Requirements

### Requirement: An unchanged request is not read again by a poll
`ReviewRound` SHALL keep each request's last paid read with the host's fingerprint (`CodeHost.requestFingerprint`) and note ids.
An auto-review poll finding both unchanged SHALL keep that verdict unpaid; anything else SHALL read as before.
#### Scenario: A poll of an unchanged request
- **WHEN** an auto-review poll finds ABC-42's fingerprint and note ids as the last paid read left them
- **THEN** no model is asked
- **WHEN** the fingerprint or a note changed, the host gives none, or you `sweep` ABC-42
- **THEN** the request is read again
