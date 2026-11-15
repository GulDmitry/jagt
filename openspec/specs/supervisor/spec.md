# supervisor Specification

## Purpose
The driver that keeps the staged jar running, restarts it, and hands its port to a hand-started jagt.

## Requirements

### Requirement: A supervisor keeps the staged jar running
`orchestrator.supervisor` SHALL select the driver: `none` (default, nobody keeps jagt alive) or `launchd` (macOS, `adapter/macos`). A
driver on a machine it does not run on SHALL be refused at startup. The `launchd` driver SHALL run the staged `jagt-run.jar` as a per-user
agent restarted whenever it exits, and no suite SHALL install a real agent.
#### Scenario: The seam is chosen
- **WHEN** `supervisor` is unset, `none` or `launchd`
- **THEN** jagt starts as today, or with the agent driver; `launchd` on Linux, or without `launchctl`, is refused with its key

### Requirement: The board installs and restarts it
`supervise` SHALL write the agent for the staged jar and load it; it SHALL refuse a jar that is not the staged one, and `none`.
`restart` SHALL restart the installed agent; before `supervise` it SHALL say so. Neither is the Master's. A rebuilt running jar SHALL be told
as "press `restart`" where a supervisor stands.
#### Scenario: A rebuilt jar
- **WHEN** a build rewrote the running jar and `supervisor` is `launchd`
- **THEN** the banner names the `restart` verb, and pressing it starts the new staged jar
#### Scenario: No supervisor
- **WHEN** the human presses `restart` or `supervise` with `supervisor` `none`
- **THEN** it is refused naming the key

### Requirement: A refused start is a page, not a log line
A start refused by a RuntimeException other than a taken port SHALL serve a page on the board's port, loopback alone.
It lists every problem, with `jagt.yml` in an editor.
Saving writes the file, keeps the previous one as `jagt.yml.bak`, closes the page and starts jagt again in the same process.
A request whose `Host` or `Origin` is not the loopback address of that port SHALL be refused.
#### Scenario: A problem is fixed on the page
- **WHEN** a start is refused with two problems and the human saves a corrected `jagt.yml`
- **THEN** the page listed both, the file is written, and jagt starts again without a new process
#### Scenario: A taken port
- **WHEN** the board's port is held by another process
- **THEN** no page is served and the start fails as before

### Requirement: A hand-started jagt gives the port to the agent
After `supervise` installed the agent, a jagt the agent did not start SHALL end through the exit seam once its answer is sent.
The answer SHALL say jagt comes back under the supervisor. A jagt the agent started SHALL NOT end, and its answer SHALL say so.
No suite SHALL end its own JVM.
#### Scenario: Pressed from a hand-started jagt
- **WHEN** `supervise` installs the agent and this process was not started by it
- **THEN** the answer says jagt comes back under launchd, and the exit seam is asked once
#### Scenario: Pressed again under launchd
- **WHEN** `supervise` runs in a process the agent started
- **THEN** the agent is written again, nothing exits, and the answer says jagt already runs under launchd
