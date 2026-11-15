# shutdown Specification

## Purpose
What jagt leaves behind when it is stopped: a state the next start goes on from. Sessions are described in
[runtime](../runtime/spec.md).

## Requirements

### Requirement: Stopping jagt ends its sessions as gone
Stopping jagt (SIGTERM) SHALL stop taking requests, finish those running and exit within 30 s.
With `onStop: end` it SHALL record every live session as gone in `state.json` and close its windows; the next
start SHALL find those tasks without a session, and a session started on one reads `task_notes.md` and
`task_context.md` first. With `onStop: keep`, the default, sessions SHALL outlive the stop.

#### Scenario: jagt is stopped with two sessions working and onStop end
- **WHEN** jagt with `onStop: end` receives SIGTERM with two sessions working
- **THEN** `state.json` holds both tasks with their sessions gone, their windows are closed and jagt exits within 30 s
- **WHEN** jagt starts again on that state
- **THEN** both tasks show no session, and a session started on one reads its notes and context first

#### Scenario: a desktop restart keeps the sessions
- **WHEN** jagt with `onStop` unset is stopped with a session working
- **THEN** the session's window stays open and `state.json` still holds it working

### Requirement: A start during the stop leaves no session running
With `onStop: end` jagt SHALL record and close sessions only after the web server stopped taking requests.
#### Scenario: a start request arrives while jagt stops
- **WHEN** jagt with `onStop: end` receives SIGTERM and a start request is still being served
- **THEN** that session is also recorded as gone and its window closed before jagt exits
