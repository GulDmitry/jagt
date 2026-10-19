## ADDED Requirements

### Requirement: The press log notes two sightings
The board SHALL log `shown:required` when a task's attention BECOMES required and `shown:jobs-failed` when the
failed-jobs chip appears, through the same batch and under the same switch as a press. A sighting is logged once per
change, never per render, and the first board painted only seeds. `shown:required` carries the task's status and
`shown:jobs-failed` null; neither carries a task id or text.

#### Scenario: A task turns required
- **WHEN** a task's attention changes to REQUIRED on a board already painted
- **THEN** one line holds `shown:required` and its status, and further renders add none

#### Scenario: The failed-jobs chip appears
- **WHEN** jobs report a failed run and the chip was hidden
- **THEN** one line holds `shown:jobs-failed` and a null status
