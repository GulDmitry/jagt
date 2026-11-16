## ADDED Requirements

### Requirement: A project's commands name programs the image has
A start SHALL resolve the first word of every project's `verifyCommand` and `setupCommand`.
A program not found SHALL be one mark on the board's head, and jagt keeps running.
The mark SHALL name the project, the program and `Extend the image (docs/server.md)`.
It SHALL end on the first read that finds the program.
A program containing `/` SHALL NOT be checked.
An absent command SHALL be no mark.

#### Scenario: A verify program the image lacks
- **WHEN** a project's `verifyCommand` starts with a program the image lacks, and you start jagt
- **THEN** the board's head names the project and the program and tells to extend the image, and jagt keeps running

#### Scenario: No command
- **WHEN** a project sets neither `verifyCommand` nor `setupCommand`
- **THEN** the head says nothing about it
