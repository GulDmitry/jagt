# 0051 — A class has one constructor, and tests use it

[← decisions](README.md)

## Status

Accepted 2026-10-19.

## Context

- Sixteen beans carried a second constructor only tests called, taking a clock, a path, a pool or a limit.
- A test through it builds a class production never builds, and its mocks stay under `MockCeilingTest` however many collaborators it hides.

## Decision

- Every constructor in `main` is called from `main`; `ProductionConstructorTest` fails the build on one that is not.
- Exempt: a record's canonical constructor, the only constructor of a Spring bean, a private constructor.
- What varies is a collaborator: `Clock`, the executors, `HttpClient` and `RetryPolicy` come from `RuntimeWiring`.
- OS name, home, environment, PATH and the running jar come from `port/Host`.
- A limit that never varies stays a constant; its test writes the volume that reaches it.
- `RuntimeWiring` declares its own `TaskScheduler`, so `@Scheduled` never lands on the board's executor.

## Rejected

- A package-private constructor for tests, kept as an overload: the pattern this ends.
- Limits as properties: an option nobody sets, in `jagt.yml`, for a test's sake.
- `System.setProperty` in a test: the suite is parallel.

## Reopen when

- A constructor needs a second shape in production, as a factory does.
- A test cannot reach a limit without writing minutes of data.
