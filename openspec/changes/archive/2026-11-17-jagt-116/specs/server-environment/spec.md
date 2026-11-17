## ADDED Requirements

### Requirement: The acceptance flows name a program the image has
The program read SHALL also resolve the first word of an enabled acceptance role's `flows.command` while its `flows.driver` is playwright.
A program not found SHALL be one mark on the board's head naming the role, the program and `Extend the image (docs/server.md)`.
It SHALL end on the first read that finds the program.

#### Scenario: A flows program the image lacks
- **WHEN** acceptance's driver is playwright and its `flows.command` starts with `npx`, which the image lacks
- **THEN** the board's head names the role and the program and tells to extend the image

#### Scenario: Flows off
- **WHEN** acceptance's `flows.driver` is none
- **THEN** the head says nothing about its command
