# Cases

One row per use case a defect broke; a fixed defect adds or updates its row in the same commit, and names its family
in a `Defect-family:` trailer. Seeded 2026-10-23 from the session ↔ Master families' fix commits since 2026-08-01.
Families:
- A text vs. text: a jagt text contradicts another, or asks what the machine refuses at that step.
- B state overwritten: a record or dedup key is overwritten, lost on restart or kept in the wrong place.
- C turn collision: two parties (human, Master, session, reviewer) act on one turn.
- D model overreach: a model decided what the machine should decide.
- E missing ending: a stop, crash, error or limit goes unseen, or two endings are merged.
- F unbounded loop: a repeat, a store or a spend has no ceiling.
- G prose routing: jagt decided by reading text.
- H a guard refuses what it should allow, or allows what it should refuse. I a vendor CLI behaves otherwise.
- J git, worktrees, several repositories. K the board. L the tests themselves. M anything else.
  H–M are counted by trailer and get rows here as their defects recur.

"pinned by": the fix commits' subjects name almost no test, so it lists the test classes the fix commits touched; `none` = no test touched.

## A — text vs. text
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| A1 | a brief or prompt rule says what the machine refuses or cannot act on → the text matches the machine, one meaning per rule | 86b25a02, 9d26669b, 3de263cf, c6c1af3d, 81999b1d, f854b297 | `SessionBriefContractTest`, `MasterModeFlowTest`, `TextBudgetTest` |
| A2 | a refusal the session cannot cure by resending its fields → comes back as a state refusal (business), not validation | 9d2099e6, 63bfea5f, fe85432b, 40872621 | `FlowEngineTest`, `TaskProvisioningTest`, `AgentStatusReportsTest`, `RevertServiceTest`, `ToolFailureTest`, `DeployServiceTest` |
| A3 | an MCP tool description is read to choose or call it → it states what the tool does now, in the brief's words (tab, revert, focus) | 06fa3c41, 5177b163, 41b5f8c5, 92fe8bed, f34e55c8 | `McpProtocolServiceTest`, `AgentSessionsTest` |
| A4 | a text names the guards and read-only branches jagt holds → it names every repository's base and deploy branch and every refusal | 1eab604c, 6e41139b, 0c3c239b, e0fe388a, 9d859b72 | `SubAgentBriefingTest`, `MasterPanelTest` |
| A5 | the Master or a reader is sent to read a ticket → only through a server it loads, else told the ticket was not read | 196acdb8, 8d9aa78b, 02788338, 29bc9e5c | `MasterPanelTest`, `ReadOnlyToolsTest`, `MeteredAssistantTest`, `TicketReaderTest` |
| A6 | a round's severity or unproven finding → one rule says what stops a round, in brief, spec and code alike | 0aa8586c, c2b0c395, 9d14aa58, 80651b5f, 7a56eb56 | `MasterPanelTest` |
| A7 | the brief needs an environment or a check → it names what the machine scripted and what the human filled, never forbids by ignorance | 36bf837f, bd7c708f, a79df791, d4c89fb8, 2e7a77c4 | none |
| A8 | the reviewer and the session share one standard and one bootstrap → the reviewer never starts as a task nor replaces a human the rules bar | af6eb566, b0d76eea, dc2902fa, de8b1815, 6e0285f1, d421463d | `MasterReviewTest`, `MasterSessionTest`, `SubAgentBriefingTest`, `HeadlessClaudeAssistantTest`, `MasterVerdictEvalTest` |
| A9 | a Master or mapper prompt carries several instructions → each is separate and complete, the message shape written once | 1fbced1a, 34f0b11e, e03f95e1, 4bfcd688, c2315352, c41a20c7, 2cf98987 | `SchemaTest`, `ProjectRoutingTest`, `DoCommandTest`, `CommandMappingCase` |
| A10 | a session is compacted or reopened in plan mode → its brief keeps task, branch and last report; the plan gate stands | 51a2ba67, 6c34876f, afe42f20 | `SessionReportsTest`, `AgentSessionControllerTest`, `AgentSessionsTest` |
| A11 | a task session briefs sub-agents → lightest model, self-contained brief, result alone, the ship's message as the ship specifies | 70bce952, b0c6e815, deb035ec | `SubAgentBriefingTest` |
| A12 | a plan or hand-back comes with a question → it is read and answered, with its facts in the prompt; a hand-back at VERIFYING says whose move | c0e97b9f, e4f5a366, b3922313 | `MasterAnswerJobTest`, `MasterReviewJobTest`, `AgentStatusReportsTest`, `RoundBriefTest` |

## B — state overwritten / dedup key in the wrong place
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| B1 | a round is handed back, fixed, rebased or re-read → its verdict is kept per diff and read back, so no round restarts from nothing | 052dd88c, 36ca2aa4, d251f05c, 5e0a43f7 | `MasterVerdictsTest`, `RoundQuotesTest`, `MasterAnswerJobTest` |
| B2 | a task is done, its worktree gone or its deploy reverted → its reviewed documents, verdict and report stay on the record | 07daec13, 8d5df1bb, a74a181b, cad7d2d8, e407a210 | `FinishedTasksTest`, `FinishedArtifactsTest`, `DoneCapabilityTest`, `WorktreeFilesTest` |
| B3 | a later instruction or answer arrives → it goes under the pending hand-off or ship, original request kept, nothing overwritten | d467fc02, e2009128 | `AgentSessionsTest`, `SubAgentBriefingTest`, `HeadlessClaudeTest` |
| B4 | jagt restarts or a job re-fires → the dedup key lives where a restart and another writer keep it, so nothing repeats | 8c57f425, 4b39427c, dc8b74a6, 1d4b8566 | `TaskStopsTest`, `RoundRelayTest`, `MasterOpenTest`, `HeadlessClaudeAssistantTest` |
| B5 | a router or launch makes nothing → nothing that would repeat it is kept or cleared, no item is turned away until restart | e7756d78, dfcec44c | `IntakeJobTest`, `TaskLauncherTest` |
| B6 | drafted replies, a polling window or a landed report belong to their round → read from the write itself, not an earlier read | 6ec70243, 8bad4394, 12225e27 | `ReviewDraftsTest`, `FlowReportsTest`, `AutoReviewSchedulerTest` |
| B7 | an unattended poll sees a deployed task → it never drags the task back to not approved | a83d2486 | `FlowRulesTest`, `MoveTest` |
| B8 | the machine reboots → every session keeps its context | 61685efd | `ClaudeAgentRuntimeTest` |
| B9 | the reviewer spends → the spend is charged to its own task | f9a54135 | `MasterSpendTest` |
| B10 | a session's spend is read from its log → each message counted once, though every content block repeats its usage | seen 2026-10-23: stats read 2× | `ClaudeSessionLogTest` |
| B13 | a task's history passes 50 steps → its opening step, who opened the task, is kept and held time skips the gap after it | seen 2026-10-24: opener lost past 50 steps | `TaskStateTest`, `HeldTimeTest` |
| B11 | two worktrees are provisioned at once, by one jagt process or several → both stay trusted in the agent CLI's config, no write lost | seen 2026-10-24: flaky trust test; on the stand 9 of 24 sessions on the trust dialog | `ClaudeTrustTest` |
| B12 | jagt restarts after a deploy → the Master presses no deploy again, the session hears "is deployed" once: both kept on the task's turn | seen 2026-10-24: NothingToDeployException and a second "is deployed" after a restart | `MasterDeployJobTest`, `DeployCheckJobTest`, `TurnsTest` |
| B14 | the agent CLI rewrites its config between provisioning and launch → the worktree's trust is written again before the launch line | seen 2026-10-24 on the stand: 9 of 24 sessions on the trust dialog | `ClaudeAgentRuntimeTest`, `TmuxSessionHostTest` |

## C — turn collision
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| C1 | the Master acts for the human → a question, plan, conflict, green request or open decision goes to the Master first, not the human | 0f42552a, 137ae7cf, 95fa45ae, 9664fc4f, b9c17a9e, bb9b5a63 | `MasterVerdictsTest`, `MasterCheckTest`, `MasterPanelTest`, `DeployConflictJobTest` |
| C2 | whose move it is → the ping, the card and the board read one Rounds answer; a move pings once, only after it has stood | 0ae5e0f6, 0f91f4ab, 0035a537, 20ede6f8, 53a17db6 | `YourMoveJobTest`, `RoundRecordTest`, `AgentStatusReportsTest`, `TaskViewsTest` |
| C3 | the human is questioning or has typed into a session → the Master neither closes nor overrules it before reading | 13f2d0e3, b7bba7f1 | `MasterShipTest`, `ClaudeTranscriptsTest` |
| C4 | a nudge or a round hits a session mid-composition, as its turn ends or hours later → it is held, never typed into the human's draft or an old cache, and sent or named failed | 1b5dd54f, 54b2efb7; seen 2026-10-24: a nudge left unsent in the input as the turn ended, twice, one for 17 minutes | `ClaudeAgentRuntimeTest`, `AgentSessionsTest`, `NudgeResubmitJobTest`, `ClaudeTranscriptsTest`, `TmuxSessionHostTest` |
| C5 | the reviewer asks → the question reaches the session that must act on the answer | 76505b05 | `MasterVerdictsTest` |
| C6 | two tasks open at once → the cap of 24 is checked and registered under one lock | fa92f2f3 | `TaskProvisioningTest` |
| C7 | a card is answered or a record comes from another stage → the card's own task answers; the record is waited for, not assumed | 9674c1f1, 901e955e | `BoardPageTest`, `ProcessRunnerTest` |
| C8 | the session reports while the Master reads its question → the turn moved on, the Master's answer is dropped and nothing sets IN_PROGRESS again | seen 2026-10-24: IN_PROGRESS set three times on a task at work | `MasterAnswerJobTest` |

## D — model overreach
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| D1 | the Master judges a round → it stops on what it cannot prove, never waves a loss through, never ships around a question | 2078122b, 9c1cb2f9, 82360b45, 6a4d3a81, f7679dba, c280b20f | `MasterPanelTest`, `MasterVerdictEvalTest`, `MasterReviewJobTest` |
| D2 | a session reports its own verdict or status → only statuses a session may report are accepted; jagt's read lands the verdict | 0829d301, ba18a8ff, 39ed0db6 | `FlowRulesTest`, `FlowCheckTest`, `VerifyJobTest`, `AgentStatusMessageTest` |
| D3 | the Master in act answers its own question or presses → no task its answer names opens, a protected path stays with the human | 245271e3, d62b9d4f, 24a87c4a, 693213d3 | `MasterShipTest`, `MasterVerdictsTest`, `HumanPathsTest` |
| D4 | a human opened a plan, or a call was denied → the human approves and grants; the Master never answers in act | 9cad8f04, eccb21fa, d5f3fe99 | `MasterAnswerJobTest`, `MasterCommandTest`, `AgentStatusReportsTest` |
| D5 | the reviewer's framing, severity and comments → fresh session per round, severity one of five, listener wiring never passed as scope | 9eb5cf78, 2a3a40b3, d733224f | `MasterSessionTest`, `SchemaTest`, `ReviewSweepServiceTest` |
| D6 | a label or command word is mapped → only schema-listed values pass; a layer label is not a repository | 1b5868c6, 452eb3b4 | `IntakeRoutingTest`, `CommandMappingEvalTest`, `PaletteContextTest` |

## E — missing ending
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| E1 | a session ends its turn or stops on any cause → the board says which ending and whose move; a turn end is a sign, not a verdict | 08d79079, 22a485f5, 2f4c6b0f, c094da30, 206bade4, fcc7e89f | `TurnEndsTest`, `MoveTest`, `SessionReportsTest`, `SessionSignsTest` |
| E2 | a usage window runs out → a typed stop, one resume line at reset, paid spend told once | 16ef94eb, 62bc22d8 | `SessionWakeJobTest`, `UsageLimitsJobTest` |
| E3 | a read fails or cannot be reached → the cause rides the answer; it is never "missing", nor logged where nobody reads | 1cf4251d, 350f42e3, b5a7c196, 2c74c5c5, 4f874409, c6b562e4, 90470aab | `HeadlessClaudeTest`, `McpHealthProbeTest`, `ReviewReaderTest` |
| E4 | a pipeline is red or the checks unreadable → the board and session hear it; a true red is never erased | 2fc9616d, e34d2bf7 | `ReviewSweepServiceTest`, `TaskStateTest` |
| E5 | a command times out or jagt stops → it dies with its descendants and tells its caller it was stopped | f2bb237f, a8b569f4, 9c990ef6 | `ProcessRunnerTest` |
| E6 | a measured verdict is turned aside by the flow → an ERROR, never a silent hold at VERIFYING | f5e047fc | `AgentStatusReportsTest` |
| E7 | a starts-stopped file is unreadable → the typed cause, not three bad endings | 793271d5 | `BoardApiControllerTest` |
| E8 | an agent exits at launch before any hook → within seconds an ended session with its last lines, the log's cause never null | seen 2026-10-24: noticed 10 min later, no cause | `LaunchExitsTest`, `TmuxSessionHostTest`, `SessionProbeTest`, `WatchdogServiceTest` |
| E9 | jagt starts with the machine checks off → its own case file is still checked; an empty book never starts | seen 2026-10-24 on the stand: 451 misses in 15 min | `StartupValidationTest` |
| E10 | a read cannot list a task's pipelines → three running since the lift stop the task with the reads' causes | seen 2026-10-24 on the stand: 8 unknown reads in 15 min, nobody told | `ChecksUnreadTest`, `RoundRecordTest`, `RoundOutcomeTest` |
| E11 | a session at NEW sits on a prompt that fires no hook, the trust dialog → within seconds a stop waiting on it, its cause named, no key sent | seen 2026-10-24 on the stand: 9 of 24 sessions, ended 10 min later as no sign of life | `LaunchExitsTest`, `ClaudeAgentRuntimeTest`, `SessionProbeTest`, `WatchdogServiceTest` |

## F — unbounded loop / no ceiling
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| F1 | a ready round holds nothing new to ship → it is not shipped again, nor sent back for a repeated finding | 9be76dd5, c7232cc2, eaaf53b0, eab76b0c, 86220a6a | `MasterShipTest`, `WorktreeChangesTest`, `MasterDecisionsTest` |
| F2 | a green verification reports through the session's own door → the task reaches its next status, not VERIFYING every 20 seconds | c63dc787 | `VerifyJobTest` |
| F3 | the Master presses, ships or starts unasked → progress is told from repeats; silent endings and a day's starts stop it | c2a6ad09, df7a8281, e20808e3 | `LoopBreakerTest`, `MasterBrakeTest`, `DailyStartsTest` |
| F4 | a store or log grows → a ceiling and something that packs or refuses past it | 3343f75e, f1b760c6, fbc2160f, 0e558ca8, 5c037ecc | `LogFileAppenderTest`, `FinishedTasksTest`, `TaskProvisioningTest` |
| F5 | a headless call spends → every call carries a spend ceiling and fails naming it | 4aa63f4a | `HeadlessClaudeTest`, `AssistantPropertiesTest` |
| F6 | an unfinished deploy, an unchanged status or a waiting close → read or asked at a bounded pace, not forever | 52c78b44, 70ba6aa6, 68e40274 | `TicketCloseJobTest`, `TaskProvisioningTest` |
| F7 | a request only a reviewer can move on is polled → read at the longest interval, not the ramp | seen 2026-10-24: 71 sweeps in 18h | `AutoReviewCadenceTest` |
| F8 | a verification stays red or the Master returns a round, new or repeated → `loopBreaker.rounds` back, the next stops the task | 0065 rule E: no ceiling; repeats asked the Master itself in `act` | `StepRelayTest`, `MasterVerdictsTest`, `LoopBreakerTest` |
| F9 | the agent declined a thread and the reviewer has not answered → it waits on the reviewer, not a round every poll | seen 2026-10-24 on the stand: 20 min of relays | `RoundOutcomeTest`, `HeadlessClaudeCodeHostAssistantTest`, `StepRelayTest` |

## G — prose routing
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| G1 | a model answer is fenced in prose or lacks its object → parsed by type, an empty answer is not unreadable and holds nothing back | 36ce177f, feb81a47, 01afa944 | `HeadlessClaudeAssistantTest`, `PipelineTest` |
| G2 | a launch refusal or a missing branch name → told by type, not by comparing a sentence | 0d999d80 | `DoCommandTest`, `TaskLaunchesTest` |
| G3 | a `do` line mentions the deploy branch → only a branch after from, into or onto refuses it | 4a41823d | `MasterShipTest` |
| G4 | a reviewer answers the agent's reply → read as answered, not as a comment nobody answered | 97ebd363 | `ReviewSweepServiceTest`, `ReviewRoundCase` |
| G5 | a field's teaching word is parsed → the parser reads the word, never a sentence | d779aa20 | `AgentStatusMessageTest`, `AgentStatusReportsTest` |

## I — vendor CLI behaviour
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| I1 | a session starts with pinned servers → the CLI's variadic `--mcp-config` never swallows the prompt | seen 2026-10-24 on the stand | `ClaudeAgentRuntimeTest` |
| I2 | a turn runs on a CLI that no longer prints the interrupt hint → its spinner line (glyph, word, ellipsis) still reads as working; a finished line or a draft never does | found 2026-10-25 on a captured Claude 2.1.308 screen | `ClaudeAgentRuntimeTest` |

## L — the tests themselves
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| L1 | a log is rolled at start → only a regular file moves; a directory of the log's name and its dotted siblings stay | seen 2026-10-24: a pitest mutant moved the surface module to surface.1 | `LogFileRollTest` |
| L2 | a stand run is recorded → its spend holds the task sessions (state.json agentSpend) beside the headless kinds and a total; a source it cannot read is "unknown" with its cause, never 0 | seen 2026-10-25: the record held jagt's reads only, ~$0.10 of a live run | `StandSpendTest` |
