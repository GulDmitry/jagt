## Why

A JVM opens its attach socket in the user's temp directory, which the session sandbox denied, so a test that mocks
through an agent failed inside it; a build daemon the session started there then failed jagt's own checks the same way.

## What Changes

- The session sandbox may also write the user's temp directory (`java.io.tmpdir` of jagt's JVM).

## Capabilities

### New Capabilities

### Modified Capabilities

- `guards`: a session may write the user's temp directory.
