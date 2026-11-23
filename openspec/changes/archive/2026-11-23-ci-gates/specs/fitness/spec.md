## ADDED Requirements

### Requirement: A CI job runs one gate task
Each job of a workflow SHALL run its work as one `gate<Job>` Gradle task, which `ci-local.sh` runs too; a version read is the one other Gradle line.
#### Scenario: A workflow runs another task
- **WHEN** a workflow line runs `./gradlew test`
- **THEN** `workflow-gradle-lines` fails naming the workflow
