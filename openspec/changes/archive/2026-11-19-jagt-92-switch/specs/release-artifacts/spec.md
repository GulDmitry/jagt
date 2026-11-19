## ADDED Requirements

### Requirement: An upgrade switches current to the kept release
A kept release SHALL carry the sha256 it was verified with as `versions/<v>/jagt.jar.sha256`.
An upgrade SHALL verify `versions/<v>/jagt.jar` against it again, then point `current` at `<v>`:
a symlink replaced atomically on macOS and Linux, the version file rewritten on Windows.
The previous version stays under `versions/`.
A mismatch, or a release kept without a checksum, leaves `current` unchanged and is refused with its reason.
#### Scenario: A kept release is verified and switched
- **WHEN** an upgrade to a kept `0.25.0` finds its jar matching the stored sha256
- **THEN** `current` names `0.25.0` and the old version stays under `versions/`
#### Scenario: The kept jar no longer matches
- **WHEN** an upgrade finds the kept jar differing from the stored sha256
- **THEN** `current` is unchanged and the failure names the mismatch
