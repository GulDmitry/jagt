## MODIFIED Requirements

### Requirement: A detached launch gets its own session
A launch SHALL leave jagt's process group, which Ctrl-C reaches, and start with SIGINT at its default: `ProcessRunner.detachedFrom`
runs it under perl's `POSIX::setsid` with SIGINT reset, or under `setsid` without perl; both `exec`, so `destroy()` reaches the app.

#### Scenario: Ctrl-C stops jagt, jagt ignores SIGINT, or a detached binary is missing
- **WHEN** the human presses Ctrl-C in jagt's terminal, or jagt ignores SIGINT - **THEN** a detached app keeps running and stops on `kill -INT`
- **WHEN** `runDetached` launches a binary that does not exist - **THEN** the wrapper exits non-zero and the launch fails
