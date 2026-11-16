## ADDED Requirements

### Requirement: The image sizes jagt's JVM for a shared pod
The image SHALL start jagt's JVM with the options in `JAGT_JAVA_OPTIONS`: a heap of at most a quarter of the container's memory,
a serial collector, exit on an out-of-memory error, and a heap dump under `/data/dumps`.
The environment SHALL override the variable. Programs a session starts SHALL NOT receive the options.

#### Scenario: The image's default
- **WHEN** the container starts with `JAGT_JAVA_OPTIONS` unset
- **THEN** jagt's JVM runs with the image's options

#### Scenario: An override
- **WHEN** the container starts with `JAGT_JAVA_OPTIONS` set
- **THEN** jagt's JVM runs with that value alone
