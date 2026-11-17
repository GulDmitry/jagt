package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.service.session.SessionHosting;
import dev.jagt.orchestrator.service.stop.ActStops;
import dev.jagt.orchestrator.service.stop.BoardStop;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.usage.ModelTier;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class AgentSessionsTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  private StateService state;
  private ConfigService config;
  private AgentSessions sessions;
  private TaskState reviewPending;
  private TaskState inProgress;
  private TaskState idleInProgress;
  private final SessionHost tmux = mock(SessionHost.class);
  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

  @BeforeEach
  void readableConfigOverAnEmptyStateFile() throws Exception {
    Path configFile =
        Files.writeString(
            root.resolve("jagt.yml"),
            "orchestrator:\n  projects: {}\n  agent:\n    submitWait: 0\n    draftPoll: 0\n");
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString())
            .withConfigFile(configFile.toString());
    OrchestratorPaths paths = new OrchestratorPaths(properties);
    state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    config =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            agentRuntime,
            new ActStops(new MasterBrake(paths), new BoardStop(paths, Clock.systemUTC())),
            new Checkers(new TaskChart(Composition.builtIn())));
    sessions =
        new AgentSessions(
            config,
            state,
            new SessionHosting(tmux, agentRuntime, new CarriedBlock(changes, agentRuntime)),
            new FlowRules(new TaskChart(Composition.builtIn())));
    when(tmux.sessionName(null)).thenReturn("jagt");
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));
    when(agentRuntime.displayName()).thenReturn("Claude");
    reviewPending = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build();
    inProgress = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
    idleInProgress =
        inProgress.withSession(
            Session.defaults().withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 5_000, null)));
  }

  @Test
  void closesTheTerminalWindowOfTheTaskItWasGiven() {
    state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
    when(tmux.killTaskWindows("jagt", "TEST-1")).thenReturn(1);

    assertThat(sessions.closeTaskTab("TEST-1")).contains("Closed 1 tab(s) for TEST-1");
  }

  @Test
  void readsTasksScreenAsPlainTextWithoutItsColourCodes() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(java.util.Optional.of("\u001B[31m✗ two tests fail\u001B[0m\n\n"));

    assertThat(sessions.screen("ABC-1")).contains("✗ two tests fail");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\u001B[>4;1m✗ two tests fail\u001B[0m",
        "\u001B(B✗ two tests fail",
        "\u001B7✗ two tests fail\u001B8"
      })
  void stripsEveryTerminalControlSequenceFromScreen(String shown) {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(java.util.Optional.of(shown));

    assertThat(sessions.screen("ABC-1")).contains("✗ two tests fail");
  }

  @Test
  void saysScreenThatCouldNotBeReadRatherThanThatNoWindowHoldsIt() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenThrow(new IllegalStateException("tmux capture-pane failed (exit 1)"));

    assertThatThrownBy(() -> sessions.screen("ABC-1")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void givesEachTaskItsOwnSessionWhenViewModeIsTabPerTask() throws Exception {
    state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
    Files.writeString(
        root.resolve("jagt.yml"),
        """
        orchestrator:
          projects: {}
          viewer:
            tmuxSession: jagt
            viewMode: tab-per-task
        """);
    when(tmux.sessionName("jagt")).thenReturn("jagt");
    when(tmux.killTaskWindows("jagt-TEST-1", "TEST-1")).thenReturn(1);

    sessions.closeTaskTab("TEST-1");

    verify(tmux).killTaskWindows("jagt-TEST-1", "TEST-1");
  }

  @Test
  void refusesToCloseTheTabOfTaskNobodyOwns() {

    assertThatThrownBy(() -> sessions.closeTaskTab("ABC-9"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ABC-9");
  }

  @Test
  void nudgesAnAgentThatIsAlreadyRunningWhenNewInstructionsArrive() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);

    assertThat(sessions.writeTaskContext("ABC-1", "new instructions")).contains("nudged");
  }

  @Test
  void pressesEnterAgainOnNudgeLeftUnsentAsTheSessionsTurnEnded() {
    state.putTask("ABC-1", inProgress);
    String left =
        """
        ────
        ❯ task_context.md changed — re-read it now and follow the
          new instructions.
        ────\
        """;
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(Optional.of("❯ "), Optional.of(left), Optional.of("❯ "));
    when(agentRuntime.draft(left))
        .thenReturn(Optional.of("task_context.md changed — re-read it now and follow the"));
    when(tmux.nudgeTaskWindow(
            "jagt",
            "ABC-1",
            "task_context.md changed — re-read it now and follow the new instructions."))
        .thenReturn(true);
    when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(true);

    String answer = sessions.writeTaskContext("ABC-1", "probes failed");

    assertThat(answer).contains("nudged");
    verify(tmux).submitTaskWindow("jagt", "ABC-1");
  }

  @Test
  void namesTheNudgeFailedWhenItNeverLeavesTheInput() {
    state.putTask("ABC-1", inProgress);
    String left = "❯ task_context.md changed — re-read it now and follow the new instructions.";
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("❯ "), Optional.of(left));
    when(agentRuntime.draft(left))
        .thenReturn(
            Optional.of(
                "task_context.md changed — re-read it now and follow the new instructions."));
    when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);
    when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(true);

    String answer = sessions.writeTaskContext("ABC-1", "probes failed");

    assertThat(answer).contains("the nudge failed", "unsent");
    verify(tmux, times(3)).submitTaskWindow("jagt", "ABC-1");
  }

  @Test
  void typesNoNudgeIntoDraftTheHumanIsComposing() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> where is the merge re"));
    when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);

    String answer = sessions.writeTaskContext("ABC-1", "new instructions");

    assertThat(answer).contains("the human is typing in the agent's window");
    verify(tmux, after(1500).never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @Test
  void sendsItsOwnUnsentNudgeInsteadOfWaitingForIt() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> nudge"));
    when(agentRuntime.holdsDraft("> nudge")).thenReturn(true);
    when(agentRuntime.draft("> nudge")).thenReturn(Optional.of(AgentSessions.NUDGE));
    when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(true);

    String answer = sessions.writeTaskContext("ABC-1", "new instructions");

    assertThat(answer).doesNotContain("the human is typing");
    verify(tmux).submitTaskWindow("jagt", "ABC-1");
  }

  @Test
  void nudgesOnceTheHumanHasSentTheirDraft() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(Optional.of("> where is the merge re"), Optional.of("> "));
    when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "new instructions");

    verify(tmux, timeout(5000)).nudgeTaskWindow("jagt", "ABC-1", AgentSessions.NUDGE);
  }

  @Test
  void typesHumansLineIntoTheRunningSessionWithoutTouchingTheRoundBriefOnDisk() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-1-demo"));
    Files.writeString(worktree.resolve("task_context.md"), "Review round for http://mr/1.");
    state.putTask(
        "ABC-1", TaskState.builder("demo", worktree.toString(), TaskStatus.REVIEW_PENDING).build());
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", "no, answer 2 differently")).thenReturn(true);

    sessions.say("ABC-1", "no, answer 2 differently");

    assertThat(Files.readString(worktree.resolve("task_context.md")))
        .isEqualTo("Review round for http://mr/1.");
  }

  @Test
  void refusesLineForTaskWhoseAgentSessionIsGoneRatherThanRespawningOverIt() {
    state.putTask(
        "ABC-1", TaskState.builder("demo", root.toString(), TaskStatus.REVIEW_PENDING).build());
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.DEAD_SHELL);
    when(agentRuntime.displayName()).thenReturn("Claude");

    assertThatThrownBy(() -> sessions.say("ABC-1", "no, answer 2 differently"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No Claude session is running for ABC-1");
  }

  @Test
  void startsFreshSessionForNewInstructionsWhenTheConversationIdledPastItsCacheLifetime() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
            .build()
            .withSession(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 5_000, null))));
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1))).thenReturn(true);

    String answer = sessions.writeTaskContext("ABC-1", "Review round for http://mr/1.");

    assertThat(answer)
        .isEqualTo(
            "Instructions written to task_context.md; a fresh session was started to read"
                + " them with task_notes.md.");
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @Test
  void startsFreshSessionForNewInstructionsWhenAnOpenTurnSignedNothingPastTheCacheLifetime() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .build()
            .withSession(Session.defaults().withMovedAt(5_000)));
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1))).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "Review round for http://mr/1.");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @Test
  void startsFreshSessionWhenTheDraftItWaitedOnOutlastedTheCacheLifetime() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
            .build()
            .withSession(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 5_000, null))));
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(Optional.of("> where is the merge re"), Optional.of("> "));
    when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1)))
        .thenReturn(false, true);

    sessions.writeTaskContext("ABC-1", "Review round for http://mr/1.");

    verify(tmux, timeout(5000)).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @Test
  void startsFreshSessionForWorkOnHandedBackRoundThoughTheConversationIsStillCached() {
    state.putTask("ABC-1", reviewPending);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);

    sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "auto,''",
        "plan,'; it plans first, and the plan is approved on the board with approve-plan'"
      })
  void namesTheSessionAndTheModeOfTheTabItStarted(String mode, String suffix) {
    state.putTask("ABC-1", inProgress);
    when(agentRuntime.displayName()).thenReturn("Claude");

    String answer = sessions.openTaskTab("ABC-1", mode);

    assertThat(answer)
        .isEqualTo(
            "New Claude session started for ABC-1 in tab 'ABC-1' of session 'jagt' (worktree "
                + root
                + ")"
                + suffix);
  }

  @Test
  void reopeningInPlanModeClosesThePlanGate() {
    state.putTask("ABC-1", inProgress);

    sessions.openTaskTab("ABC-1", "plan");

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @ParameterizedTest
  @CsvSource({"VERIFYING", "REVIEW_PENDING", "DEPLOYED"})
  void refusesPlanModeWhereNoPlanCanComeFirstAndLeavesTheGateAndTheTabAlone(TaskStatus status) {
    state.putTask("ABC-1", TaskState.builder("proj", root.toString(), status).build());

    assertThatThrownBy(() -> sessions.openTaskTab("ABC-1", "plan"))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isNotEqualTo(PlanGate.REQUIRED);
    verifyNoInteractions(tmux);
  }

  @Test
  void writesLineForFreshSessionInsteadOfTypingItIntoOneIdlePastItsCacheLifetime()
      throws Exception {
    state.putTask("ABC-1", idleInProgress);
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1))).thenReturn(true);

    String answer = sessions.say("ABC-1", "jagt was restarted.");

    assertThat(answer).contains("a fresh session was started");
    assertThat(Files.readString(root.resolve("task_context.md"))).contains("jagt was restarted.");
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @ParameterizedTest
  @CsvSource({"true, true", "false, false"})
  void readsTaskColdOnlyWhenItsSessionIdledPastTheCacheLifetime(
      boolean pastLifetime, boolean cold) {
    state.putTask("ABC-1", idleInProgress);
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1)))
        .thenReturn(pastLifetime);

    assertThat(sessions.cold("ABC-1")).isEqualTo(cold);
  }

  @Test
  void confirmsLineWasSaidToTheRunningAgent() {
    state.putTask(
        "ABC-1", TaskState.builder("demo", root.toString(), TaskStatus.REVIEW_PENDING).build());
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", "answer 2 differently")).thenReturn(true);

    assertThat(sessions.say("ABC-1", "answer 2 differently")).isEqualTo("Said to the agent.");
  }

  @Test
  void answersTheWindowStateOfTheTasksOwnSession() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.DEAD_SHELL);

    assertThat(sessions.windowState("ABC-1")).isEqualTo(SessionHost.WindowState.DEAD_SHELL);
  }

  @Test
  void startsTheFreshSessionOnTheModelTheNotesName() throws Exception {
    Files.writeString(root.resolve("task_notes.md"), "kept the retry\nnext-model: sonnet\n");
    state.putTask("ABC-1", reviewPending);

    sessions.openTaskTab("ABC-1", "auto");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "sonnet");
  }

  @Test
  void startsTheFreshSessionOnTheConfiguredModelWhenTheNotesNameNone() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n  agent:\n    model: sonnet\n");
    state.putTask("ABC-1", reviewPending);

    sessions.openTaskTab("ABC-1", "auto");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "sonnet");
  }

  @Test
  void startsTheFreshSessionOnTheNotesModelOverTheConfiguredOne() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n  agent:\n    model: sonnet\n");
    Files.writeString(root.resolve("task_notes.md"), "next-model: opus\n");
    state.putTask("ABC-1", reviewPending);

    sessions.openTaskTab("ABC-1", "auto");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "opus");
  }

  @Test
  void restartsStoppedSessionOnTheTierTheTaskWasRaisedTo() throws Exception {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 1)));
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));
    when(agentRuntime.tiers()).thenReturn(List.of("sonnet", "opus"));

    String said = sessions.restartOnTier("ABC-1");

    assertThat(said).isEqualTo("session of ABC-1 started again on opus");
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "opus");
    assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().tier()).current())
        .isEqualTo("opus");
  }

  @Test
  void startsTheNextSessionBackOnTheConfiguredModelAfterTierUp() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n  agent:\n    model: sonnet\n");
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 1)));
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));
    when(agentRuntime.tiers()).thenReturn(List.of("sonnet", "opus"));
    when(agentRuntime.displayName()).thenReturn("Claude");

    sessions.openTaskTab("ABC-1", "auto");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "sonnet");
    assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().tier()).raised()).isFalse();
  }

  @Test
  void recordsTheDefaultLadderOnStartedSessionAboveItsOwnModel() {
    state.putTask("ABC-1", reviewPending);
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));
    when(agentRuntime.tiers()).thenReturn(List.of("sonnet", "opus"));
    when(agentRuntime.displayName()).thenReturn("Claude");

    sessions.openTaskTab("ABC-1", "auto");

    assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().tier()).models())
        .containsExactly("", "opus");
  }

  @Test
  void refusesToRestartOnTierForSessionWithNoLadder() {
    state.putTask("ABC-1", inProgress);

    assertThatThrownBy(() -> sessions.restartOnTier("ABC-1")).isInstanceOf(Refusal.class);
  }

  @Test
  void startsTheFreshSessionOnTheCliDefaultWhenNeitherNotesNorConfigNameModel() {
    state.putTask("ABC-1", reviewPending);

    sessions.openTaskTab("ABC-1", "auto");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
  }

  @Test
  void startsTheFreshSessionOnTheDefaultModelWhenTheRuntimeCannotChoose() throws Exception {
    Files.writeString(root.resolve("task_notes.md"), "next-model: sonnet\n");
    state.putTask("ABC-1", reviewPending);
    when(agentRuntime.models()).thenReturn(List.of());
    when(agentRuntime.displayName()).thenReturn("Codex");

    sessions.openTaskTab("ABC-1", "auto");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
  }

  @Test
  void nudgesTheRunningConversationWhenItIsStillWithinItsCacheLifetime() {
    state.putTask("ABC-1", reviewPending);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "Review round for http://mr/1.");

    verify(tmux, never())
        .openTaskWindow(
            anyString(), anyString(), anyString(), any(), any(), eq(false), anyString());
  }

  @Test
  void reEntersTheSessionOfTaskWhoseWindowIsGoneRatherThanDroppingTheRelay() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);

    assertThat(sessions.writeTaskContext("ABC-1", "new instructions")).contains("re-entered");
    verify(tmux).reviveTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any());
  }

  @Test
  void reEntersWhatTheDeadSessionKnewWhenFocusingTaskTheMachineRestartedUnder() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);
    when(agentRuntime.displayName()).thenReturn("Claude");

    assertThat(sessions.focusTask("ABC-1").revivedAs()).isEqualTo("Claude");
    verify(tmux).reviveTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any());
  }

  @Test
  void startsFreshSessionWhenHumanRestartsTheAgentInsteadOfReEnteringTheOldOne() {
    state.putTask("ABC-1", inProgress);
    when(agentRuntime.displayName()).thenReturn("Claude");

    assertThat(sessions.openTaskTab("ABC-1", "auto")).contains("New Claude session started");
    verify(tmux)
        .openTaskWindow(
            anyString(), anyString(), eq("ABC-1"), any(), any(), eq(false), anyString());
    verify(tmux, never()).reviveTaskWindow(anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void nudgesTheSessionHumanJustRestartedRatherThanStartingItFreshAgain() {
    state.putTask("ABC-1", idleInProgress);
    when(agentRuntime.displayName()).thenReturn("Claude");
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1))).thenReturn(true);
    sessions.openTaskTab("ABC-1", "auto");
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "new instructions");

    verify(tmux).nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString());
    verify(tmux, times(1))
        .openTaskWindow(
            anyString(), anyString(), eq("ABC-1"), any(), any(), anyBoolean(), anyString());
  }

  @Test
  void replacesTheRelayForNewRoundOfWork() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-1-demo"));
    Files.writeString(worktree.resolve("task_context.md"), "STALE: last round");
    state.putTask(
        "ABC-1", TaskState.builder("demo", worktree.toString(), TaskStatus.CI_POLLING).build());

    sessions.writeTaskContext("ABC-1", "NEW ROUND: fix the pipeline");

    assertThat(Files.readString(worktree.resolve("task_context.md")))
        .isEqualTo("NEW ROUND: fix the pipeline")
        .doesNotContain("STALE");
  }

  @Test
  void keepsPendingInstructionAboveWhatIsRelayedUnderIt() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-42-demo"));
    Files.writeString(
        worktree.resolve("task_context.md"), "Resolve the conflict in /src/ABC-42-deploy.");
    state.putTask(
        "ABC-42",
        TaskState.builder("demo", worktree.toString(), TaskStatus.DEPLOY_CONFLICT).build());

    sessions.relayUnder("ABC-42", "Keep both imports.", false);

    assertThat(Files.readString(worktree.resolve("task_context.md")))
        .contains("Resolve the conflict in /src/ABC-42-deploy.", "Keep both imports.");
  }

  @Test
  void replacesTheEarlierAdditionUnderPendingInstructionSoTheFileStaysBounded() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-42-demo"));
    Files.writeString(
        worktree.resolve("task_context.md"), "Resolve the conflict in /src/ABC-42-deploy.");
    state.putTask(
        "ABC-42",
        TaskState.builder("demo", worktree.toString(), TaskStatus.DEPLOY_CONFLICT).build());
    sessions.relayUnder("ABC-42", "Keep both imports.", false);

    sessions.relayUnder("ABC-42", "Drop the old import.", false);

    assertThat(Files.readString(worktree.resolve("task_context.md")))
        .contains("Resolve the conflict in /src/ABC-42-deploy.", "Drop the old import.")
        .doesNotContain("Keep both imports.");
  }

  @Test
  void keepsTheAgentsViewerOpenAfterTheLastTaskByDefault() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  projects: {}\n  viewer:\n    tmuxSession: jagt\n");
    when(tmux.sessionName("jagt")).thenReturn("jagt");

    assertThat(sessions.viewerToClose()).isEmpty();
  }

  @Test
  void closesTheAgentsViewerAfterTheLastTaskWhenReservingItIsTurnedOff() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  projects: {}\n  viewer:\n    tmuxSession: jagt\n    keepViewer: false\n");
    when(tmux.sessionName("jagt")).thenReturn("jagt");

    assertThat(sessions.viewerToClose()).contains("jagt");
  }

  @Test
  void pressesKeyInTheWindowOfRunningAgent() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.pressTaskWindow("jagt", "ABC-1", SessionHost.Key.ESCAPE)).thenReturn(true);

    assertThat(sessions.press("ABC-1", SessionHost.Key.ESCAPE)).isEqualTo("Pressed ESCAPE.");
  }

  @Test
  void refusesKeyWhereNoAgentRuns() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.DEAD_SHELL);
    when(agentRuntime.displayName()).thenReturn("Claude");

    assertThatThrownBy(() -> sessions.press("ABC-1", SessionHost.Key.TAB))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No Claude session is running");
    verify(tmux, never()).pressTaskWindow(anyString(), anyString(), any());
  }

  @ParameterizedTest
  @CsvSource({"true,true", "false,false"})
  void countsTheInterruptOnlyWhereTheKeyLandsOnWorkingTurn(boolean landed, boolean counted) {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("esc to interrupt"));
    when(agentRuntime.working("esc to interrupt")).thenReturn(true);
    when(tmux.pressTaskWindow("jagt", "ABC-1", SessionHost.Key.ESCAPE)).thenReturn(landed);

    assertThat(sessions.interrupt("ABC-1")).isEqualTo(counted);
  }

  @Test
  void interruptsNothingWhereTheSessionWaitsAtItsPrompt() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("❯ "));

    assertThat(sessions.interrupt("ABC-1")).isFalse();
    verify(tmux, never()).pressTaskWindow(anyString(), anyString(), any());
  }

  @Test
  void saysTheHumanIsComposingWhereTheWindowHoldsDraft() {
    state.putTask("ABC-1", inProgress);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> where is the merge re"));
    when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);

    assertThat(sessions.composing("ABC-1")).isTrue();
  }

  @Test
  void saysTheHumanIsNotComposingWhereTheWindowHoldsNoDraft() {
    state.putTask("ABC-1", inProgress);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> "));

    assertThat(sessions.composing("ABC-1")).isFalse();
  }

  @Test
  void nudgesAtOnceWhereTheScreenCannotBeReadSinceNoDraftIsKnown() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenThrow(new IllegalStateException("capture-pane failed"));
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", AgentSessions.NUDGE)).thenReturn(true);

    String answer = sessions.writeTaskContext("ABC-1", "new instructions");

    assertThat(answer).contains("the agent was nudged");
  }

  @Test
  void nudgesNothingOnceTheWindowIsGoneWhileTheHumanWasTyping() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1"))
        .thenReturn(
            SessionHost.WindowState.AGENT_RUNNING,
            SessionHost.WindowState.AGENT_RUNNING,
            SessionHost.WindowState.MISSING);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(Optional.of("> where is the merge re"), Optional.of("> "));
    when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "new instructions");

    verify(tmux, after(2500).never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @Test
  void pressesEnterOnTheNudgeLeftUnsentInAnIdleSessionsInput() {
    state.putTask("ABC-1", inProgress);
    String idle = "❯ task_context.md changed — re-read it now and follow the new instructions.";
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of(idle));
    when(agentRuntime.draft(idle))
        .thenReturn(
            Optional.of(
                "task_context.md changed — re-read it now and follow the new instructions."));
    when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(true);

    assertThat(sessions.resubmitUnsentNudge("ABC-1")).isTrue();
    verify(tmux).submitTaskWindow("jagt", "ABC-1");
  }

  @Test
  void startsFreshSessionInsteadOfPressingEnterOnTheNudgeOfSessionColdPastItsCacheLifetime() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .build()
            .withSession(Session.defaults().withMovedAt(5_000)));
    String idle = "❯ task_context.md changed — re-read it now and follow the new instructions.";
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of(idle));
    when(agentRuntime.draft(idle))
        .thenReturn(
            Optional.of(
                "task_context.md changed — re-read it now and follow the new instructions."));
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1))).thenReturn(true);

    boolean pressed = sessions.resubmitUnsentNudge("ABC-1");

    assertThat(pressed).isFalse();
    verify(tmux, never()).submitTaskWindow(anyString(), anyString());
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
  }

  @Test
  void pressesNothingOnTheHumansOwnDraft() {
    state.putTask("ABC-1", inProgress);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("screen"));
    when(agentRuntime.draft("screen"))
        .thenReturn(Optional.of("where is the merge request for the nudge"));

    assertThat(sessions.resubmitUnsentNudge("ABC-1")).isFalse();
    verify(tmux, never()).submitTaskWindow(anyString(), anyString());
  }

  @Test
  void pressesNothingWhileSessionsTurnIsRunning() {
    state.putTask("ABC-1", inProgress);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("screen"));
    when(agentRuntime.working("screen")).thenReturn(true);
    when(agentRuntime.draft("screen"))
        .thenReturn(
            Optional.of(
                "task_context.md changed — re-read it now and follow the new instructions."));

    assertThat(sessions.resubmitUnsentNudge("ABC-1")).isFalse();
    verify(tmux, never()).submitTaskWindow(anyString(), anyString());
  }

  @Test
  void retiresWhatProvisioningWroteOutsideTheWorktreeThroughTheRuntime() {

    sessions.forgetWorktree(Path.of("/wt"));

    verify(agentRuntime).retireWorktree(Path.of("/wt"));
  }

  @Test
  void tellsWhatTheAgentPrintedLastBeforeItExited() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    when(tmux.exitTail("jagt", "ABC-1")).thenReturn(Optional.of("Error: usage limit reached"));

    assertThat(sessions.exitTail("ABC-1")).contains("Error: usage limit reached");
  }

  @Test
  void tellsWhyTheSessionWaitsAtPromptJagtNeverAnswers() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("Do you trust this folder?"));
    when(agentRuntime.unansweredPrompt("Do you trust this folder?"))
        .thenReturn(Optional.of("a trust dialog"));

    assertThat(sessions.unansweredPrompt("ABC-1")).contains("a trust dialog");
  }

  @Test
  void answersAnAdmissionWithWhatTheRuntimeSaidOfTheTasksWorktree() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    when(agentRuntime.admit(Path.of("/wt"), Path.of("/other")))
        .thenReturn(AgentRuntime.Admission.GRANTED);

    assertThat(sessions.admit("ABC-1", Path.of("/other")))
        .isEqualTo(AgentRuntime.Admission.GRANTED);
  }

  @Test
  void tellsTheCallerFreshSessionWasStartedForRelayedRound() {
    state.putTask("ABC-1", reviewPending);

    String answer = sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

    assertThat(answer).contains("a fresh session was started");
  }

  @Test
  void tellsTheCallerTheSessionWasReenteredWhenInstructionsAreRelayedUnderPendingOne() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.DEPLOY_CONFLICT).build());

    String answer = sessions.relayUnder("ABC-1", "Keep both imports.", false);

    assertThat(answer).contains("was down, so it was re-entered");
  }

  @Test
  void typesLineIntoTheRunningSessionAndSaysSo() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", "use the cache")).thenReturn(true);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> "));

    String answer = sessions.say("ABC-1", "use the cache");

    assertThat(answer).isEqualTo("Said to the agent.");
    verify(tmux).nudgeTaskWindow("jagt", "ABC-1", "use the cache");
  }

  @Test
  void typesLineIntoColdSessionSittingAtAnOpenDialogInsteadOfStartingFreshOne() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .build()
            .withSession(Session.defaults().withMovedAt(5_000)));
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("dialog"));
    when(agentRuntime.unansweredPrompt("dialog"))
        .thenReturn(Optional.of("waiting on a permission dialog"));
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1))).thenReturn(true);
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", "1")).thenReturn(true);

    String answer = sessions.say("ABC-1", "1");

    assertThat(answer).isEqualTo("Said to the agent.");
    verify(tmux, never())
        .openTaskWindow(
            anyString(), anyString(), anyString(), any(), any(), anyBoolean(), anyString());
  }

  @Test
  void refusesLineWhereNoSessionIsRunningForTheTask() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);

    assertThatThrownBy(() -> sessions.say("ABC-1", "use the cache"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("restart the agent");
    verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @Nested
  class WhenTheLineLeftTheInputButNeverSent {

    @BeforeEach
    void theLineSitsInTheAgentsInput() {
      state.putTask("ABC-1", inProgress);
      when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
      when(tmux.nudgeTaskWindow("jagt", "ABC-1", "use the cache")).thenReturn(true);
      when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> use the cache"));
      when(agentRuntime.draft("> use the cache")).thenReturn(Optional.of("use the cache"));
    }

    @Test
    void refusesLineTheWindowTookButClosedBeforeItLeftTheInput() {
      when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(false);

      assertThatThrownBy(() -> sessions.say("ABC-1", "use the cache"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("its window closed before the line left its input");
    }

    @Test
    void refusesLineThatStaysUnsentAfterEnterWasPressedFourTimes() {
      when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(true);

      assertThatThrownBy(() -> sessions.say("ABC-1", "use the cache"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "still sat unsent in the agent's input after Enter was pressed 4 times");
      verify(tmux, times(3)).submitTaskWindow("jagt", "ABC-1");
    }
  }

  @ParameterizedTest
  @CsvSource({
    "task_context.md changed — re-read it,true",
    "task_context.md changed — re-read i,false"
  })
  void takesAnInputAsTheNudgeOnlyWhenItIsAtLeastHalfOfIt(String draft, boolean pressed) {
    state.putTask("ABC-1", inProgress);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("screen"));
    when(agentRuntime.draft("screen")).thenReturn(Optional.of(draft));
    when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(true);

    assertThat(sessions.resubmitUnsentNudge("ABC-1")).isEqualTo(pressed);
  }

  @Test
  void pressesNothingMoreWhereTheWindowRefusedTheEnter() {
    state.putTask("ABC-1", inProgress);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("screen"));
    when(agentRuntime.draft("screen"))
        .thenReturn(
            Optional.of(
                "task_context.md changed — re-read it now and follow the new instructions."));
    when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(false);

    assertThat(sessions.resubmitUnsentNudge("ABC-1")).isFalse();
  }

  @Test
  void pressesNothingWhereTheScreenCannotBeRead() {
    state.putTask("ABC-1", inProgress);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenThrow(new IllegalStateException("capture-pane failed"));

    assertThat(sessions.resubmitUnsentNudge("ABC-1")).isFalse();
    verify(tmux, never()).submitTaskWindow(anyString(), anyString());
  }

  @Test
  void saysTheNudgeWasDeliveredWhenOnlyTheLastEnterSentIt() {
    state.putTask("ABC-1", inProgress);
    String left = "❯ " + AgentSessions.NUDGE;
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(
            Optional.of("❯ "),
            Optional.of(left),
            Optional.of(left),
            Optional.of(left),
            Optional.of("❯ "));
    when(agentRuntime.draft(left)).thenReturn(Optional.of(AgentSessions.NUDGE));
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", AgentSessions.NUDGE)).thenReturn(true);
    when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(true);

    String answer = sessions.writeTaskContext("ABC-1", "probes failed");

    assertThat(answer).contains("nudged");
    verify(tmux, times(3)).submitTaskWindow("jagt", "ABC-1");
  }

  @Test
  void refusesLineWhenJagtWasStoppedBeforeItWasSeenToLeaveTheInputAndKeepsTheStop() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", "use the cache"))
        .thenAnswer(call -> {
          Thread.currentThread().interrupt();
          return true;
        });

    Throwable refused = catchThrowable(() -> sessions.say("ABC-1", "use the cache"));
    boolean stopKept = Thread.interrupted();

    assertThat(refused)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("jagt stopped before the line was seen to leave the input");
    assertThat(stopKept).isTrue();
  }

  @Test
  void pressesEnterAgainOnTheNudgeTypedOnceTheHumanSentTheirDraftAndItStayedUnsent() {
    state.putTask("ABC-1", inProgress);
    String draft = "> where is the merge re";
    String left = "❯ " + AgentSessions.NUDGE;
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(Optional.of(draft), Optional.of("> "), Optional.of(left), Optional.of("> "));
    when(agentRuntime.holdsDraft(draft)).thenReturn(true);
    when(agentRuntime.draft(left)).thenReturn(Optional.of(AgentSessions.NUDGE));
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", AgentSessions.NUDGE)).thenReturn(true);
    when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "new instructions");

    verify(tmux, timeout(5000)).submitTaskWindow("jagt", "ABC-1");
  }

  @Test
  void logsThatFreshSessionWasStartedBecauseTheRoundWasAlreadyHandedBack() {
    state.putTask("ABC-1", reviewPending);
    try (FakeLogCapture log = new FakeLogCapture(AgentSessions.class)) {

      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

      assertThat(log.events())
          .filteredOn(event -> "agent session started fresh".equals(event.getMessage()))
          .flatExtracting(ILoggingEvent::getKeyValuePairs)
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains("cause=the round was already handed back");
    }
  }

  @Test
  void startsFreshSessionForColdPromptWhateverTheConversationsCacheSays() {
    state.putTask("ABC-1", inProgress);

    sessions.relayUnderCold("ABC-1", "and handle empty files");

    assertThat(root.resolve("task_context.md"))
        .content()
        .startsWith("and handle empty files\n\n## Carried from the previous session");
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
  }

  @Test
  void startsTheSessionRecordOverWhenFreshSessionIsStarted() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
            .build()
            .withSession(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 5_000, null))
                    .withMovedAt(5_000)));

    sessions.relayUnderCold("ABC-1", "and handle empty files");

    assertThat(state.task("ABC-1").orElseThrow().session())
        .isEqualTo(Session.startedOver(Session.defaults()));
  }

  @Test
  void refusesPressedFocusWithTheCauseWhereTheSessionsWindowsCannotBeListed() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1"))
        .thenThrow(
            new SessionHost.WindowsUnreadable(
                "tmux list-windows jagt failed (exit 1): server timed out"));

    assertThatThrownBy(() -> sessions.focusTask("ABC-1"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("server timed out");
  }

  @Nested
  class WhileTheFreshStartWaitsOnTheHuman {

    private final AtomicBoolean typing = new AtomicBoolean(true);
    private final AtomicBoolean ownTurnOpen = new AtomicBoolean(false);
    private final AtomicBoolean windowUnreadable = new AtomicBoolean(false);
    private final AtomicReference<SessionHost.WindowState> window =
        new AtomicReference<>(SessionHost.WindowState.AGENT_RUNNING);

    @BeforeEach
    void theHumanSitsInTheAgentsWindow() {
      state.putTask("ABC-1", reviewPending.withSession(Session.defaults().withMovedAt(5_000)));
      when(tmux.taskWindowState("jagt", "ABC-1"))
          .thenAnswer(call -> {
            if (windowUnreadable.get()) {
              throw new SessionHost.WindowsUnreadable(
                  "tmux list-windows jagt failed (exit 1): server timed out");
            }
            return window.get();
          });
      when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("typed"));
      when(agentRuntime.holdsDraft("typed")).thenAnswer(call -> typing.get());
      when(agentRuntime.humanExchangeOpen(root, Set.of(AgentSessions.NUDGE)))
          .thenAnswer(call -> ownTurnOpen.get());
    }

    @Test
    void freshStartTellsTheCallerItWaitsWhereTheHumanHasUnsentInput() {
      String answer = sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

      assertThat(answer).contains("starts when they are done");
    }

    @Test
    void freshStartOpensNothingWhileTheHumanKeepsTyping() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux, never())
          .openTaskWindow(
              anyString(), anyString(), anyString(), any(), any(), anyBoolean(), anyString());
    }

    @Test
    void freshStartOpensOnceTheHumanHasBeenGoneOnTwoConsecutiveReads() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      typing.set(false);

      sessions.startWaitingFreshWhenFree("ABC-1");
      verify(tmux, never())
          .openTaskWindow(
              anyString(), anyString(), anyString(), any(), any(), anyBoolean(), anyString());
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    }

    @Test
    void freshStartWaitsAgainWhenTheHumanTypesAgainBetweenTheTwoReads() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      typing.set(false);
      sessions.startWaitingFreshWhenFree("ABC-1");
      typing.set(true);
      sessions.startWaitingFreshWhenFree("ABC-1");
      typing.set(false);

      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux, never())
          .openTaskWindow(
              anyString(), anyString(), anyString(), any(), any(), anyBoolean(), anyString());
    }

    @Test
    void freshStartOpensOnceTheHumanIsFinallyDoneAfterTypingAgain() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      typing.set(false);
      sessions.startWaitingFreshWhenFree("ABC-1");
      typing.set(true);
      sessions.startWaitingFreshWhenFree("ABC-1");
      typing.set(false);
      sessions.startWaitingFreshWhenFree("ABC-1");

      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    }

    @Test
    void freshStartOpensOnceTheHumansWindowIsGone() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      window.set(SessionHost.WindowState.MISSING);

      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    }

    @Test
    void freshStartWaitsWhileTheHumansOwnTurnIsOpenAndOpensOnceItEnds() {
      typing.set(false);
      ownTurnOpen.set(true);

      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");
      verify(tmux, never())
          .openTaskWindow(
              anyString(), anyString(), anyString(), any(), any(), anyBoolean(), anyString());
      ownTurnOpen.set(false);
      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    }

    @Test
    void freshStartDoesNotWaitOnTurnJagtOpened() {
      typing.set(false);

      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    }

    @Test
    void freshStartKeepsWaitingThroughReadThatFailsAndOpensOnceTheHumanIsGone() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      windowUnreadable.set(true);
      typing.set(false);
      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");
      verify(tmux, never())
          .openTaskWindow(
              anyString(), anyString(), anyString(), any(), any(), anyBoolean(), anyString());
      windowUnreadable.set(false);

      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    }
  }

  @Test
  void freshStartForRoundCarriesTheOldConversation() throws Exception {
    state.putTask("ABC-1", reviewPending);
    when(agentRuntime.sessionLogOf(root)).thenReturn(Optional.of(Path.of("/logs/old.jsonl")));
    when(agentRuntime.conversationTail(root, 16_000, Set.of(AgentSessions.NUDGE)))
        .thenReturn(Optional.of("human: it is the second route"));
    when(changes.history(any(TaskState.class), eq(40))).thenReturn("abc123 Add export");

    sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

    assertThat(Files.readString(root.resolve("task_context.md")))
        .startsWith("Review round for http://mr/1.")
        .contains("## Carried from the previous session", "human: it is the second route");
  }

  @Test
  void freshStartAnnouncesItsWaitOncePerTask() {
    state.putTask("ABC-1", reviewPending);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("typed"));
    when(agentRuntime.holdsDraft("typed")).thenReturn(true);
    try (FakeLogCapture log = new FakeLogCapture(AgentSessions.class)) {

      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      int afterFirst =
          (int)
              log.events().stream()
                  .filter(event -> "fresh start waits".equals(event.getMessage()))
                  .count();
      sessions.relayFresh("ABC-1", "Review round for http://mr/2.");

      assertThat(afterFirst).isEqualTo(1);
      assertThat(log.events())
          .filteredOn(event -> "fresh start waits".equals(event.getMessage()))
          .hasSize(1);
    }
  }

  @Test
  void logWrittenAfterTheMomentReadsAsLoggedSince() throws Exception {
    Path log = Files.writeString(root.resolve("session.jsonl"), "{}\n");
    Files.setLastModifiedTime(log, FileTime.from(Instant.parse("2026-11-05T07:00:01Z")));
    state.putTask("ABC-1", inProgress);
    when(agentRuntime.sessionLogOf(root)).thenReturn(Optional.of(log));

    boolean logged = sessions.loggedSince("ABC-1", 1793862000000L);

    assertThat(logged).isTrue();
  }

  @Test
  void logLastWrittenBeforeTheMomentDoesNotReadAsLoggedSince() throws Exception {
    Path log = Files.writeString(root.resolve("session.jsonl"), "{}\n");
    Files.setLastModifiedTime(log, FileTime.from(Instant.parse("2026-11-05T06:59:59Z")));
    state.putTask("ABC-1", inProgress);
    when(agentRuntime.sessionLogOf(root)).thenReturn(Optional.of(log));

    boolean logged = sessions.loggedSince("ABC-1", 1793862000000L);

    assertThat(logged).isFalse();
  }

  @Test
  void typesLineBehindTheShellEscapeIntoTheRunningSession() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(agentRuntime.shellEscape()).thenReturn(Optional.of("!"));
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", "!ls -la")).thenReturn(true);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> "));

    String answer = sessions.run("ABC-1", "ls -la");

    assertThat(answer).isEqualTo("Run in the agent's window.");
    verify(tmux).nudgeTaskWindow("jagt", "ABC-1", "!ls -la");
  }

  @Test
  void typesLineBehindTheShellEscapeIntoColdSessionInsteadOfRelayingItAsWords() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .build()
            .withSession(Session.defaults().withMovedAt(5_000)));
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1))).thenReturn(true);
    when(agentRuntime.shellEscape()).thenReturn(Optional.of("!"));
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", "!ls")).thenReturn(true);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> "));

    sessions.run("ABC-1", "ls");

    verify(tmux).nudgeTaskWindow("jagt", "ABC-1", "!ls");
    verify(tmux, never())
        .openTaskWindow(
            anyString(), anyString(), anyString(), any(), any(), anyBoolean(), anyString());
    assertThat(root.resolve("task_context.md")).doesNotExist();
  }

  @Test
  void refusesToRunLineWhereTheRuntimeHasNoShellEscape() {
    state.putTask("ABC-1", inProgress);
    when(agentRuntime.shellEscape()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sessions.run("ABC-1", "ls")).isInstanceOf(Refusal.class);
    verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @Test
  void logsThatLineRanAndItsLengthNeverTheLine() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(agentRuntime.shellEscape()).thenReturn(Optional.of("!"));
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", "!echo hunter2")).thenReturn(true);
    when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> "));
    try (FakeLogCapture log = new FakeLogCapture(AgentSessions.class)) {

      sessions.run("ABC-1", "echo hunter2");

      assertThat(log.events())
          .filteredOn(event -> "line run in agent".equals(event.getMessage()))
          .singleElement()
          .satisfies(event -> {
            assertThat(event.getKeyValuePairs())
                .extracting(pair -> pair.key + "=" + pair.value)
                .contains("chars=12");
            assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                .doesNotContain("hunter2");
          });
    }
  }
}
