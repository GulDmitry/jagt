## Why

The Master's QA read a two-file rules edit and flagged its wording, round after round: a diff with no code has nothing
to test. It also asked for tests of configuration and of a tested library's own calls, which pin nothing of the project.

## What Changes

- An `applies` glob starting with `!` names paths a role never reads; a role whose changed paths are all such is skipped.
- The shipped brief's `QA` row names docs and configuration with `!`.
- Its question: a test pins the project's own logic; configuration, metaprogramming and a plain library call need none.

## Capabilities

### Modified Capabilities

- `master`: a role's `applies` takes exclusions; the shipped `QA` skips a docs-and-configuration diff.
