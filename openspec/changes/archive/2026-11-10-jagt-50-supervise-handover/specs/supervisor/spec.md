## ADDED Requirements
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
