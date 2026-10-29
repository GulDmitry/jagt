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
- H a guard refuses what it should allow, or allows what it should refuse. I a vendor CLI behaves otherwise. J git, worktrees,
  several repositories. K the board. L the tests themselves. M anything else. H–M are counted by trailer and get rows here as their defects recur.

"pinned by": the fix commits' subjects name almost no test, so it lists the test classes the fix commits touched; `none` = no test touched.
## A — text vs. text
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| A1 | a brief or prompt rule says what the machine refuses or cannot act on → the text matches the machine, one meaning per rule | 86b25a02, 9d26669b, 3de263cf, c6c1af3d, 81999b1d, f854b297 | `SessionBriefContractTest`, `MasterModeFlowTest`, `TextBudgetTest` |
| A2 | a refusal the session cannot cure by resending its fields → comes back as a state refusal (business), not validation | 9d2099e6, 63bfea5f, fe85432b, 40872621 | `FlowEngineTest`, `TaskProvisioningTest`, `AgentStatusReportsTest`, `RevertServiceTest`, `ToolFailureTest`, `DeployServiceTest`, `FlowRulesTest` |
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
| B11, B14 | two worktrees are provisioned at once, by one jagt process or several → both stay trusted in the agent CLI's config, no write lost; the CLI rewrites its config between provisioning and launch → the worktree's trust is written again before the launch line | seen 2026-10-24: flaky trust test; on the stand 9 of 24 sessions on the trust dialog | `ClaudeTrustTest`, `ClaudeAgentRuntimeTest`, `TmuxSessionHostTest` |
| B12 | jagt restarts after a deploy → the Master presses no deploy again, the session hears "is deployed" once: both kept on the task's turn | seen 2026-10-24: NothingToDeployException and a second "is deployed" after a restart | `MasterDeployJobTest`, `DeployCheckJobTest`, `TurnsTest` |
| B15 | jagt restarts while a running session holds the turn → it is asked once to repeat its last report; no open session turn or no running session is asked nothing | seen 2026-10-25: a report sent while jagt was down was lost and the session waited | `RestartAskJobTest` |
| B16 | a task is reopened and done again the same day → each `done` keeps its own snapshot, named to the second, never over the first | seen 2026-10-28: a same-day second `done` replaced the first snapshot's documents | `FinishedArtifactsTest` |
| B17 | the same job fails on the same pushed tip and the brief quoting it reads differently → the round is not relayed again; a round is digested by its facts, not its wording | seen 2026-10-28: one failure was relayed twice in two hours as a fresh session, three rounds in 24h stopped the task | `TaskStopsTest`, `WorktreeInspectionTest` |
| B18 | a session asks a question and a sweep reads its request green → the card still asks and shows the question; only the session's next report or a human answer clears it | seen 2026-10-29: found by the status × condition inventory | `FlowReportsTest` |
| B19 | a sweep reads a request green while its session works on the task (follow-up work, a hand-back, a plan) → the status stays the session's, only the checks and approval are recorded | seen 2026-10-29: found by the status × condition inventory | `FlowRulesTest` |
| B20 | a `CI_FAILED` task's pipeline is re-run and a poll reads it green with nothing unresolved → it moves on as a `CI_POLLING` one does, no session needed | seen 2026-10-29: found by the status × condition inventory | `FlowRulesTest` |
| B21 | a request git found merged is no longer inside its target (the base was reset, the merge reverted) and the host never said merged → it reads not merged again within 5 minutes, `ship` is allowed and auto-review polls it; the host's word and a fetch that fails change nothing | seen 2026-10-29: found by the status × condition inventory | `MergedRequestsTest`, `MergedReadJobTest`, `WorktreeInspectionTest` |

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
| C9 | the human presses a verb (ship) while the Master reads the hand-back → the verdict and the Master's answer are dropped, claimed against status and turn in one write, the session told after it, a failed telling undone and stopped; the human's status and the session's one step stand, no round counted for a dropped verdict | seen 2026-10-25 on the stand: ship overwritten by IN_PROGRESS, two contradictory steps, task stopped on a miss | `MasterVerdictsTest`, `StepRelayTest`, `FlowReportsTest`, `MasterAnswerJobTest` |
| C10 | a step is relayed and jagt dies while the session is told → the turn and its digest are recorded before the send, so a press again tells it nothing twice; a failed send puts the turn back and stops the task for the human | seen 2026-10-25 on the stand (run -8): relayed 11:10:48, turn opened 11:10:49 | `StepRelayTest` |
| C11 | the session asked the human and the checks are still red → the sweep relays nothing and starts no session until the human answers | seen 2026-10-28: a fresh session ended the one holding the question, three times in 24h, and the ceiling stopped the task | `RoundRelayTest` |
| C12 | a verification outlasts the round it was taken for (the human shipped meanwhile) → the result is dropped, never relayed, reported or stopped on | seen 2026-10-28: 2.5-minute probes failed after a ship, the case door refused the relay and the task stopped | `VerifyJobTest` |

## D — model overreach
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| D1 | the Master judges a round → it stops on what it cannot prove, never waves a loss through, never ships around a question | 2078122b, 9c1cb2f9, 82360b45, 6a4d3a81, f7679dba, c280b20f | `MasterPanelTest`, `MasterVerdictEvalTest`, `MasterReviewJobTest` |
| D2 | a session reports its own verdict or status → only statuses a session may report are accepted; jagt's read lands the verdict | 0829d301, ba18a8ff, 39ed0db6 | `FlowRulesTest`, `FlowCheckTest`, `VerifyJobTest`, `AgentStatusMessageTest` |
| D3 | the Master in act answers its own question or presses → no task its answer names opens, a protected path stays with the human | 245271e3, d62b9d4f, 24a87c4a, 693213d3 | `MasterShipTest`, `MasterVerdictsTest`, `HumanPathsTest` |
| D4 | a human opened a plan, or a call was denied → the human approves and grants; the Master never answers in act | 9cad8f04, eccb21fa, d5f3fe99 | `MasterAnswerJobTest`, `MasterCommandTest`, `AgentStatusReportsTest` |
| D5 | the reviewer's framing, severity and comments → fresh session per round, severity one of five, listener wiring never passed as scope | 9eb5cf78, 2a3a40b3, d733224f | `MasterSessionTest`, `SchemaTest`, `ReviewSweepServiceTest` |
| D6 | a label or command word is mapped → only schema-listed values pass; a layer label is not a repository | 1b5868c6, 452eb3b4 | `IntakeRoutingTest`, `CommandMappingEvalTest`, `PaletteContextTest` |
| D7 | an item's labels name several projects → every one is attached; a repository leaves a multi-repo task only by an approved plan line | seen 2026-10-28: labels `sng` + `sobrado-connect` placed in one project, the other dropped unlogged | `ProjectRoutingTest`, `PlanRepositoriesTest`, `HandBackTest` |

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
| E12 | the Master presses a verb the machine refuses (a deploy of a branch already on dev) → one WARN naming task, cmd and cause, the press not counted, the other tasks still served; never an ERROR from the job runner | seen 2026-10-26 in the live log: master-deploy failed with Nothing to deploy | `MasterPressTest` |
| E14 | a task the Master moved once goes silent after the human's or the session's own step → no strike; only a silence right after the Master's own move counts toward stopping `act` | seen 2026-10-28: three such silences stopped `act` twice in an hour | `MasterBrakeTest` |
| E15 | a task stopped on unreadable checks or an unchanged failure gets a new request → the stop lifts, a new request is a new pipeline | seen 2026-10-28: a stop on a merged request's pipeline refused the replacing request's sweeps | `TaskStateTest` |
| E13 | a change typed into the terminal at DEPLOYED, REVIEWED, APPROVED, CI_POLLING or CI_FAILED ends its turn unseen → the turn end is refused once, naming IN_PROGRESS or reverting it; the second end on the same tree passes | seen 2026-10-26: tree dirty after a deploy, no round reached the Master, nothing shipped | `TurnEndsTreeTest`, `TurnTreeTest` |
| E15 | `resume <url>` cannot read the request → the refusal names what stopped the read on one line, clipped as the sweep line is, never "cause in the log" | seen 2026-10-28: the sweep named its failure, resume still said "cause in the log" | `TaskResumeTest` |
| E16 | tmux cannot list a session's windows → no window is opened, closed or reported missing and the cause is logged; an absent session still lists none | seen 2026-10-28: a failed list read as no `master` window, a second one opened beside the first | `TmuxSessionHostTest` |
| E17 | a resolved deploy conflict waits on a stopped task → nothing is pressed, one INFO says it waits on the lift, a press logs its result, and a landed deploy forgets the conflict record | seen 2026-10-28: `master finishes deploy` every 20 s, ten times, each press refused by the stop guard | `ConflictFinishTest`, `ConflictHandOffTest`, `TaskStateTest` |
| E18 | a pipeline dies on infrastructure (a lost runner) → jagt re-runs it after each wait and the session hears nothing; after the last wait or a refused re-run the task stops as `INFRA`, never a question to a human | seen 2026-10-29: ABC-42 and ABC-43 went red on a runner and the session asked about the infrastructure | `RoundOutcomeTest`, `RedCausesTest`, `InfraRetryJobTest`, `InfraRetriesTest` |
| E19 | a stop an outside condition set (`INFRA`, `CHECKS`) stands and a read lists the task's checks, not red → the stop is voided, one INFO says so, and a deploy conflict held by it finishes on the next tick; a red or unreadable read, or a stop for judgement, stands | seen 2026-10-29: an INFRA stop set while the host was down held a resolved deploy conflict two hours after the host came back | `TaskStopsTest`, `StopReadsTest`, `StopReadJobTest`, `ConflictFinishTest` |
| E20 | a stop ends on a read that listed the checks → the task's checks and approval are the ones that read listed, not those of the sweep before the outage; a request the stop was counted on gone, closed unmerged or unlinked voids it with `request=gone`, while another request of the task gone, or a read the host or CLI could not answer, keeps it | seen 2026-10-29: found by the status × condition inventory | `StopReadsTest`, `RoundRecordTest`, `RoundReadingTest`, `TaskStopsTest`, `HeadlessClaudeCodeHostAssistantTest` |
| E22 | a deployed, conflicted or reverted task's open request dies on infrastructure → no retry is armed or sent and no `INFRA` stop is set | seen 2026-10-29: found by the status × condition inventory | `RedCausesTest`, `InfraRetriesTest` |
| E23 | a SHIPS or STALLED stop stands and a human deploys the task → the stop lifts once it is `DEPLOYED` and the deploy check reads the landed change; the Master's or jagt's deploy past a stop stays refused | seen 2026-10-29: found by the status × condition inventory | `DeployLiftsStopTest` |
| E24 | an `UNDELIVERED` stop stands (a step never landed) and the session's window runs again → the stop ends, one INFO says `read=session alive`; a window still not running keeps it | seen 2026-10-29: found by the status × condition inventory | `SessionAliveJobTest` |
| E25 | a `WAIT` stop stands (a dependency stayed down) → jagt says `wait-over` again at the last backoff, a wait keeps the stop and the next wake, any other report of the session ends it | seen 2026-10-29: found by the status × condition inventory | `ReportEffectsTest` |
| E26 | a deploy's pipeline fails after the deploy landed → jagt reads it by the deploy commit and the session is asked only once green; red on the code hands the deploy worktree back as a conflict, its staged fix committed and pushed by the deploy, and `revert` takes fix and merge out; red on infrastructure is retried | seen 2026-10-29: a resolved conflict landed and its pipeline failed on an import unused only in the merge result; the session checked in a worktree without the merge and the human fixed it by hand | `DeployCheckJobTest`, `DeployPipelinesTest`, `ConflictHandOffTest`, `GitDeployTest`, `RevertServiceTest`, `CliPipelineRetryTest` |

## F — unbounded loop / no ceiling
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| F1 | a ready round holds nothing new to ship → it is not shipped again, nor sent back for a repeated finding | 9be76dd5, c7232cc2, eaaf53b0, eab76b0c, 86220a6a | `MasterShipTest`, `WorktreeChangesTest`, `MasterDecisionsTest` |
| F2, F12 | a green verification reports through the session's own door → the task reaches its next status, not VERIFYING every 20 seconds; a question or an unchanged tree sits at VERIFYING → no probe runs and nothing is relayed twice; an unchanged failing tree stops the task for the human | c63dc787; seen 2026-10-26: an unchanged tree's probe failure was relayed three times over a question; the Master's answer was dropped and the task stopped | `VerifyJobTest`, `FlowReportsTest` |
| F3 | the Master presses, ships or starts unasked → progress is told from repeats; silent endings and a task's hour of unasked moves stop it | c2a6ad09, df7a8281, e20808e3 | `LoopBreakerTest`, `MasterBrakeTest`, `AutoReviewSchedulerTest` |
| F4 | a store or log grows → a ceiling and something that packs or refuses past it | 3343f75e, f1b760c6, fbc2160f, 0e558ca8, 5c037ecc | `LogFileAppenderTest`, `FinishedTasksTest`, `TaskProvisioningTest` |
| F5 | a headless call spends → every call carries a spend ceiling and fails naming it | 4aa63f4a | `HeadlessClaudeTest`, `AssistantPropertiesTest` |
| F6 | an unfinished deploy, an unchanged status or a waiting close → read or asked at a bounded pace, not forever | 52c78b44, 70ba6aa6, 68e40274 | `TicketCloseJobTest`, `TaskProvisioningTest` |
| F7 | a request only a reviewer can move on is polled → read at the longest interval, not the ramp | seen 2026-10-24: 71 sweeps in 18h | `AutoReviewCadenceTest` |
| F8 | a verification stays red or the Master returns a round, new or repeated → `loopBreaker.rounds` back, the next stops the task | 0065 rule E: no ceiling; repeats asked the Master itself in `act` | `StepRelayTest`, `MasterVerdictsTest`, `LoopBreakerTest` |
| F9 | the agent declined a thread and the reviewer has not answered → it waits on the reviewer, not a round every poll | seen 2026-10-24 on the stand: 20 min of relays | `RoundOutcomeTest`, `HeadlessClaudeCodeHostAssistantTest`, `StepRelayTest` |
| F10 | a reviewer's concession ends a pushed-back thread and the session has nothing to answer → the round is relayed once; the same open threads with the same newest notes are not relayed again, and only rounds bringing no new note count toward the ceilings | seen 2026-10-26: a bot's concession on a pushed-back thread was relayed every poll, the Master re-shipped nothing three times and the task stopped | `TaskStopsTest`, `LoopBreakerTest`, `ReviewFactsTest`, `HeadlessClaudeCodeHostAssistantTest` |
| F11 | a hand-back with nothing new is shipped again → the Master ships only uncommitted work, an unpushed HEAD or drafted replies, and never twice on one tree | seen 2026-10-26: the Master shipped an unchanged pushed branch five times; each ship found nothing and auto-review relayed again; seen 2026-10-27: an untracked `.omc/` kept 4 ships due on one tree | `ShipDueTest`, `WorktreeChangesTest`, `WorktreeInspectionTest` |
| F13 | a new turn hands back the diff and kind the Master already judged → its verdict is reused, not reviewed again | seen 2026-10-26: 17 Master reviews asked over one unchanged diff, one per turn | `RoundQuotesTest`, `MasterReviewTest` |
| F14 | a session that changes no code (a stage check) keeps asking → each Master answer lands in the same session; past three the answer changes the approach, the ninth stops for the human | seen 2026-10-27: 4 session restarts in 7 minutes, each losing what the session had tried | `MasterVerdictsTest`, `MasterAnswerJobTest` |
| F15 | a round's note fingerprint is blank (note ids unknown) → it is never a repeat: no stalled run, no futile ship counted | seen 2026-10-27: three blank-fingerprint rounds stalled a task and a SHIPS stop read "ships without a new note in 24h: 4" | `LoopBreakerTest`, `MasterVerdictsTest` |
| F16 | a session stays silent across several stale windows → one alert at the silence's start; only a new silence alerts again | seen 2026-10-28: one 35-minute silence logged 7 alerts | `WatchdogServiceTest` |
| F17 | a not-ready plan is returned after each rework → only rounds answering one unchanged report count toward `loopBreaker.rounds` | seen 2026-10-29: three reworked plans in 24h stopped a task as a loop | `LoopBreakerTest`, `MasterVerdictsTest` |

## G — prose routing
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| G1 | a model answer is fenced in prose or lacks its object → parsed by type, an empty answer is not unreadable and holds nothing back | 36ce177f, feb81a47, 01afa944 | `HeadlessClaudeAssistantTest`, `PipelineTest` |
| G2 | a launch refusal or a missing branch name → told by type, not by comparing a sentence | 0d999d80 | `DoCommandTest`, `TaskLaunchesTest` |
| G3 | a `do` line mentions the deploy branch → only a branch after from, into or onto refuses it | 4a41823d | `MasterShipTest` |
| G4 | a reviewer answers the agent's reply → read as answered, not as a comment nobody answered | 97ebd363 | `ReviewSweepServiceTest`, `ReviewRoundCase` |
| G5 | a field's teaching word is parsed → the parser reads the word, never a sentence | d779aa20 | `AgentStatusMessageTest`, `AgentStatusReportsTest` |

## H — a guard
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| H1 | a session writes the verdict file itself, a ready one naming the round in front → no verdict: only a file hashing to what jagt recorded counts, and a review no role read is a question | seen 2026-10-27, a probe: an empty-reads verdict shipped a request unread | `MasterReviewTest` |
| H2 | a stop counted on a request outlives it, the session opens a follow-up request → the stop lifts with the replaced request and the count restarts; a stop counted on no request stands | seen 2026-10-28: auto-review refused a follow-up request as stopped, a SHIPS stop counted on the merged one | `TaskStateTest` |
| H3 | a reader asks who it is signed in as or which tools the host has, to reach a failed job's log → `whoami` and `discover*` pass beside `get*`, every write verb stays refused; an unreadable round names its cause | seen 2026-10-28: `whoami` and `discover_tools` refused, the sweep said "cause in the log" | `ReadOnlyToolsTest`, `RoundReadingTest` |
| H4 | the Master ships a task whose request git finds merged → `ship` is withheld and a press records no step, so no ship after the merge counts toward the loop stop | seen 2026-10-28: a fourth ship after the request merged tripped a SHIPS stop on a request that no longer existed | `MergedGuardTest`, `MoveTest`, `WorktreeInspectionTest` |
| H5 | a round carries red checks outside the code and a thread with the reviewer's new answer → the hand-back is refused until `review_replies.md` is written since the relay, and a standing question holds back only the checks | seen 2026-10-29: the session took the checks' "change nothing" route and left the thread unanswered; later sweeps read the round as unchanged | `HandBackDueTest`, `TaskStopsTest`, `RoundRelayTest` |

## I — vendor CLI behaviour
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| I1 | a session starts with pinned servers → the CLI's variadic `--mcp-config` never swallows the prompt | seen 2026-10-24 on the stand | `ClaudeAgentRuntimeTest` |
| I2 | a turn runs on a CLI that no longer prints the interrupt hint → its spinner line (glyph, word, ellipsis) still reads as working; a finished line or a draft never does | found 2026-10-25 on a captured Claude 2.1.308 screen | `ClaudeAgentRuntimeTest` |
| I3 | a session's Gradle or Maven runs under Claude's sandbox → it reaches Maven Central, so the sandbox is off unless `sandbox.enabled: true` | seen 2026-10-26: `Could not HEAD 'https://repo.maven.apache.org/...' > Operation not permitted`, curl in the same shell got 200 | `WorktreeSandboxTest` |
| I4 | a session sits idle past its cache, the CLI compacts it unasked → the compaction's start does not read as work, so the next prompt goes to a fresh session | seen 2026-10-27: compacted 54 min after the turn, a prompt 21 min later passed the gate into the old conversation | `ClaudeAgentRuntimeTest` |
| I5 | a session idles past its cache and jagt restarts or loses the hook's mark → a prompt still goes to a fresh session, idle read from the transcript's turn end | seen 2026-10-27: turn ended 13:16Z, a prompt at 18:06Z passed the gate as warm | `ColdSessionsTest`, `ClaudeTranscriptsTest` |
| I6 | a prompt is typed into the fresh session a cold prompt went to → it is judged by its own log, which does not exist yet, so it is never relayed again | seen 2026-10-28 08:02Z: the newest log in the directory was the old session's, an endless restart every ~2.5 s | `ColdSessionsTest`, `ClaudeTranscriptsTest` |

## J — git, worktrees, several repositories
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| J1 | a long-lived branch's request listed commits its moved target already held; the session was forbidden to rebase → it rebases onto the target and lease-pushes, ordered by the `ship` step | seen 2026-10-28 | `SubAgentBriefingTest`, `SessionBriefTest`, `ToolGateTest` |
| J2 | the host says a request merged by squash or fast-forward, git finds no merge commit → the review read's `merged` word records it, and `ship` is withheld | seen 2026-10-28: a GitLab request merged, the task stayed REVIEWED with `merged` unset | `RoundReadingTest` |
| J3 | every request of a task is merged, the task still `REVIEWED` → its card reads `merged`, offers `done` quietly and is no longer polled | seen 2026-10-29: a squash-merged request left the card "awaiting approval" and polled | `MoveTest`, `TaskViewTest`, `AutoReviewCadenceTest` |

## K — the board
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| K1 | a task jagt stopped shows `lift the stop` → the line adds the stop's short reason and its hover the evidence, the advice and the time | seen 2026-10-28: the card said to read the hover, which held nothing | `TaskViewTest`, `BoardCardTest` |
| K2 | a REVIEWED task waits on a human approval past the 24h poll window → it stays CI's with no attention asked and is polled again on a lengthening interval | seen 2026-10-29: ABC-42 turned amber `check for the approval` at 24h though only the reviewer could move it | `AutoReviewCadenceTest`, `ConfigServiceTest` |

## L — the tests themselves
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| L1 | a log is rolled at start → only a regular file moves; a directory of the log's name and its dotted siblings stay | seen 2026-10-24: a pitest mutant moved the surface module to surface.1 | `LogFileRollTest` |
| L2 | a stand run is recorded → its spend holds the task sessions (state.json agentSpend) beside the headless kinds and a total; a source it cannot read is "unknown" with its cause, never 0 | seen 2026-10-25: the record held jagt's reads only, ~$0.10 of a live run | `StandSpendTest` |
| L3 | a stand run ends with the task stopped → its outcome reads "stopped, human needed" with the stop's trigger and evidence in the run record, never a silent timeout | seen 2026-10-25: a stop after a 940 s host sleep ended three runs as a bare IN_PROGRESS | `TimelineTest`, `StandOutcomeTest` |
| L4 | a mutation run ends → it passes only on a real move: the session reported, then the task deployed, the session asked, a refusal was recorded or the reviewer is waiting, and no defect stop (stalled, ships, checks); owner YOU alone is not one | seen 2026-10-25: restart-mid-turn, self-approval and self-deploy passed on a frozen session handed to the human | `MutationProgressTest` |
| L5 | more stand runs start than the cap → the extra start waits until a live run closes, whatever the JUnit pool holds | seen 2026-10-25: a fixed pool of 6 held by containers left one live run, 12 queued serially | `StandSlotsTest` |
| L6 | a task rests owned by YOU where the stub has no verb to press → a judge run is held once the timeline (status, relay, Master read) was quiet for 60 s; an act run never on that, the Master presses on YOU's turn: only the 10 min hold or a jagt stop | seen 2026-10-25: 3 polls cut act runs off before the Master's round | `RunEndTest`, `HumanStubTest`, `TimelineTest` |
| L7 | a relay's step is checked against the cases → at the status jagt's `turn opened` line names (the status the door checked); only a line without one falls back to the history at its time | seen 2026-10-25: every Master round-not-ready was flagged at IN_PROGRESS, the status after the Master's own move | `StepsWithinCasesTest` |
| L8 | a run reaches DEPLOY_CONFLICT → only the deploy-conflict mutation passes on its own holds (any other mutation fails naming the unexpected conflict, and is not held at it): the conflict step reached the session and nothing was force-pushed, the run held as soon as the relay is seen or after 60 s without one; the stub still presses no deploy there | seen 2026-10-25: both deploy-conflict runs failed with "neither a deploy, a question nor a refusal" on the held conflict | `MutationProgressTest`, `RunEndTest`, `MutationPlanTest` |
| L9 | a world event waits for a step relay → restart and human-line fire on the first recognised relay, and a run reaching its status keeps going up to a quiet minute while an event is unfired | seen 2026-10-25: a run deployed before a second relay, so the event never fired | `WorldEventTest`, `RunEndTest` |
| L10 | jagt is restarted mid-run → the first jagt's output and backend log are kept as `.before-restart` (read by the relay invariant, copied into the evidence), the second never overwrites them | seen 2026-10-25: a restart-mid-turn failure kept only the second jagt's output | `JagtProcessTest` |
| L11 | a mutated run ends with a violation and a lack → both are reported in one failure, neither hides the other | seen 2026-10-25: a missing progress assertion stopped the run before its invariant violations were read | `MutationStandTest` (model run) |
| L12 | the review sweep says "waiting on the reviewer" three polls in a row and nothing else moves for 3 s → the run ends "waiting on the reviewer", a pass-eligible ending, instead of idling to the timeout | seen 2026-10-25: the stand has no reviewer to answer, so runs with an answered thread waited out the clock | `BackendSignalsTest`, `RunEndTest`, `StandOutcomeTest`, `MutationProgressTest` |
| L13 | jagt is restarted mid-run → the second binds the first's port and the marker keeps it | seen 2026-10-25, jagt-stand-68761-8: second jagt on another port, the session's `.mcp.json` still named the first, its status reports got ECONNREFUSED | `StandRestartTest` |
| L14 | the deploy-conflict mutation runs in judge → the task rests at DEPLOY_CONFLICT owned by YOU and no `conflict` step is relayed (`relays-in: act` in `mutations.md`: only act hands the session the conflict, decision 0013); the run is held at once there and passes without the step, act still needs it | seen 2026-10-25: a judge run waited a minute for and then failed on a conflict step jagt never sends | `MutationBookTest`, `MutationProgressTest`, `RunEndTest` |
| L15 | a board test breaks the shared job → its context is discarded after it, so a later test in the class never opens on a failed job row | seen 2026-10-28: the jobs table test found a failed row before it broke anything whenever the table rendered ahead of its first count; red with a 1.5 s wait before that count | `BoardPopupsAndTabsTest` |

## M — anything else
| id | the case, one line | defects | pinned by |
|----|--------------------|---------|-----------|
| M1 | a ticket keeps its acceptance criteria in a field the tracker's default read omits → the plan carries each criterion tagged here, elsewhere or wrong, and a plan without the section is refused | seen 2026-10-26: 16 criteria never reached `plan.md`, the plan was approved without them | `PlanCriteriaTest`, `HandBackTest`, `AgentStatusReportsTest` |
| M2 | a ship finds nothing to commit, push or post → its session may reply `no_changes`, which both ship cases take; the task is not stopped | seen 2026-10-26: a ship with nothing new to push replied no_changes, the task stopped and new review comments were never relayed | `CaseDoorTest` |
| M3 | a fresh session starts on a task past NEW and is only asked a question → it keeps the board's status and ends its turn unreported; a change begun reports IN_PROGRESS | seen 2026-10-28: a question on a reviewed task set IN_PROGRESS, the session idled, the watchdog flagged it stopped | `SessionBriefTest` |
| M4 | a launch names a bare command found nowhere on PATH → it fails at once naming it, whatever the machine load | seen 2026-10-28: a loaded run let the wrapper outlive its 200 ms check and the launch answered with a process | `ProcessRunnerTest` |
