## MODIFIED Requirements

### Requirement: A missing engine, library or tool fails the load
A fitness function whose engine, library or tool is neither on the classpath nor installed SHALL fail the load, unless it is `optional`.
The `FitnessConfigException` names the fitness function and what is missing: `MISSING_ENGINE` or `MISSING_TOOL`.
Core SHALL know an engine only through its provider and the libraries it names.
A provider that cannot load fails as `PROVIDER_FAILED`, a name served twice as `DUPLICATE_ENGINE` or `DUPLICATE_ID`.
#### Scenario: An engine is missing
- **WHEN** a fitness function names an engine no provider serves
- **THEN** loading fails with `MISSING_ENGINE` naming the fitness function and the engine
#### Scenario: A provider cannot be loaded
- **WHEN** a services file names a provider class that is absent or whose initialisation throws
- **THEN** loading fails with `PROVIDER_FAILED` naming the service, with the cause attached
#### Scenario: A name served twice
- **WHEN** two providers carry one engine name or one `Fitness` id
- **THEN** loading fails with `DUPLICATE_ENGINE` or `DUPLICATE_ID` naming it
#### Scenario: A tool is missing
- **WHEN** a fitness function needs a tool that is not installed and is not `optional`
- **THEN** loading fails with `MISSING_TOOL` naming the fitness function and the tool
