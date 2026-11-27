package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestAgentSessionsBuilder.agentSessions;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.usage.ModelTier;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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

class AgentSessionsTest {

  @TempDir Path root;

  private StateService state;
  private AgentSessions sessions;
  private TaskState reviewPending;
  private TaskState inProgress;
  private TaskState idleInProgress;
  private final SessionHost tmux = mock(SessionHost.class);
  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

  @BeforeEach
  void readableConfigOverAnEmptyStateFile() throws Exception {
    state = stateService().withRoot(root).build();
    sessions = agentSessions().withRoot(root).build(state, tmux, agentRuntime, changes);
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
            session: jagt
            viewMode: tab-per-task
        """);
    when(tmux.sessionName("jagt")).thenReturn("jagt");
    when(tmux.killTaskWindows("jagt-TEST-1", "TEST-1")).thenReturn(1);

    sessions.closeTaskTab("TEST-1");

    verify(tmux).killTaskWindows("jagt-TEST-1", "TEST-1");
  }

  @Test
  void namesTheWindowOfTaskByItsIdWhateverNameItWasAskedBy() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());

    assertThat(sessions.windowOf("a1"))
        .isEqualTo(new dev.jagt.orchestrator.service.session.SessionWindow("jagt", "ABC-1"));
  }

  @Test
  void namesTheTaskWindowInTheSessionOfItsOwnWhenViewModeIsTabPerTask() throws Exception {
    state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
    Files.writeString(
        root.resolve("jagt.yml"),
        """
        orchestrator:
          projects: {}
          viewer:
            session: jagt
            viewMode: tab-per-task
        """);
    when(tmux.sessionName("jagt")).thenReturn("jagt");

    assertThat(sessions.windowOf("TEST-1").session()).isEqualTo("jagt-TEST-1");
  }

  @Test
  void refusesToNameTheWindowOfTaskNobodyOwns() {
    assertThatThrownBy(() -> sessions.windowOf("ABC-9")).isInstanceOf(Refusal.class);
  }

  @Test
  void refusesToCloseTheTabOfTaskNobodyOwns() {

    assertThatThrownBy(() -> sessions.closeTaskTab("ABC-9"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ABC-9");
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
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
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

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
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
        .thenReturn(Optional.of("> where is the merge re"))
        .thenReturn(Optional.of("> "));
    when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1)))
        .thenReturn(false, true);

    sessions.writeTaskContext("ABC-1", "Review round for http://mr/1.");

    verify(tmux, timeout(5000))
        .openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
    verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
  }

  @Test
  void startsFreshSessionForWorkOnHandedBackRoundThoughTheConversationIsStillCached() {
    state.putTask("ABC-1", reviewPending);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);

    sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
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

    assertThat(state.task("ABC-1").orElseThrow().gate().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @ParameterizedTest
  @CsvSource({"VERIFYING", "REVIEW_PENDING", "DEPLOYED"})
  void refusesPlanModeWhereNoPlanCanComeFirstAndLeavesTheGateAndTheTabAlone(TaskStatus status) {
    state.putTask("ABC-1", TaskState.builder("proj", root.toString(), status).build());

    assertThatThrownBy(() -> sessions.openTaskTab("ABC-1", "plan"))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));

    assertThat(state.task("ABC-1").orElseThrow().gate().planGate()).isNotEqualTo(PlanGate.REQUIRED);
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
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
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

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "sonnet");
  }

  @Test
  void startsTheFreshSessionOnTheConfiguredModelWhenTheNotesNameNone() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n  agent:\n    model: sonnet\n");
    state.putTask("ABC-1", reviewPending);

    sessions.openTaskTab("ABC-1", "auto");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "sonnet");
  }

  @Test
  void startsTheFreshSessionOnTheNotesModelOverTheConfiguredOne() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n  agent:\n    model: sonnet\n");
    Files.writeString(root.resolve("task_notes.md"), "next-model: opus\n");
    state.putTask("ABC-1", reviewPending);

    sessions.openTaskTab("ABC-1", "auto");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "opus");
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
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "opus");
    assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().tier()).current())
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

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "sonnet");
    assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().tier()).raised())
        .isFalse();
  }

  @Test
  void recordsTheDefaultLadderOnStartedSessionAboveItsOwnModel() {
    state.putTask("ABC-1", reviewPending);
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));
    when(agentRuntime.tiers()).thenReturn(List.of("sonnet", "opus"));
    when(agentRuntime.displayName()).thenReturn("Claude");

    sessions.openTaskTab("ABC-1", "auto");

    assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().tier()).models())
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

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
  }

  @Test
  void startsTheFreshSessionOnTheDefaultModelWhenTheRuntimeCannotChoose() throws Exception {
    Files.writeString(root.resolve("task_notes.md"), "next-model: sonnet\n");
    state.putTask("ABC-1", reviewPending);
    when(agentRuntime.models()).thenReturn(List.of());
    when(agentRuntime.displayName()).thenReturn("Codex");

    sessions.openTaskTab("ABC-1", "auto");

    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
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
            anyString(), anyString(), eq("ABC-1"), any(), any(), eq(LaunchMode.WORK), anyString());
    verify(tmux, never()).reviveTaskWindow(anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void keepsTheAgentsViewerOpenAfterTheLastTaskByDefault() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n  viewer:\n    session: jagt\n");
    when(tmux.sessionName("jagt")).thenReturn("jagt");

    assertThat(sessions.viewerToClose()).isEmpty();
  }

  @Test
  void closesTheAgentsViewerAfterTheLastTaskWhenReservingItIsTurnedOff() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  projects: {}\n  viewer:\n    session: jagt\n    keepViewer: false\n");
    when(tmux.sessionName("jagt")).thenReturn("jagt");

    assertThat(sessions.viewerToClose()).contains("jagt");
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
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
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
  void tellsTheCallerFreshSessionWasStartedForRoundRelayedBelowPendingInstruction() {
    state.putTask("ABC-1", reviewPending);

    String answer = sessions.relayUnderFresh("ABC-1", "Review round for http://mr/1.");

    assertThat(answer).contains("a fresh session was started");
  }

  @Test
  void logsThatFreshSessionWasStartedBecauseTheRoundWasAlreadyHandedBack() {
    state.putTask("ABC-1", reviewPending);
    try (FakeLogCapture log = new FakeLogCapture(FreshStarts.class)) {

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
    verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
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

    assertThat(state.task("ABC-1").orElseThrow().work().session())
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
      when(agentRuntime.humanExchangeOpen(root, Set.of(AgentWindows.NUDGE)))
          .thenAnswer(call -> ownTurnOpen.get());
    }

    @Test
    void freshStartTellsTheCallerItWaitsWhereTheHumanHasUnsentInput() {
      String answer = sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

      assertThat(answer).contains("starts when they are done");
    }

    @Test
    void coldPromptTheHumanTypedStartsFreshSessionAtOnceWhileTheirOwnTurnIsOpen() {
      ownTurnOpen.set(true);

      boolean started = sessions.relayUnderCold("ABC-1", "and handle empty files");

      assertThat(started).isTrue();
      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
    }

    @Test
    void coldPromptStartsNothingWhereTheAgentsQuestionIsUnanswered() {
      when(agentRuntime.unansweredPrompt("typed")).thenReturn(Optional.of("Proceed?"));

      boolean started = sessions.relayUnderCold("ABC-1", "and handle empty files");

      assertThat(started).isFalse();
      verify(tmux, never())
          .openTaskWindow(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
    }

    @Test
    void freshStartOpensNothingWhileTheHumanKeepsTyping() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux, never())
          .openTaskWindow(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
    }

    @Test
    void freshStartOpensOnceTheHumanHasBeenGoneOnTwoConsecutiveReads() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      typing.set(false);

      sessions.startWaitingFreshWhenFree("ABC-1");
      verify(tmux, never())
          .openTaskWindow(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
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
          .openTaskWindow(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
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

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
    }

    @Test
    void freshStartOpensOnceTheHumansWindowIsGone() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      window.set(SessionHost.WindowState.MISSING);

      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
    }

    @Test
    void freshStartWaitsWhileTheHumansOwnTurnIsOpenAndOpensOnceItEnds() {
      typing.set(false);
      ownTurnOpen.set(true);

      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");
      verify(tmux, never())
          .openTaskWindow(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
      ownTurnOpen.set(false);
      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
    }

    @Test
    void freshStartDoesNotWaitOnTurnJagtOpened() {
      typing.set(false);

      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
    }

    @Test
    void freshStartKeepsWaitingThroughReadThatFailsAndOpensOnceTheHumanIsGone() {
      sessions.relayFresh("ABC-1", "Review round for http://mr/1.");
      windowUnreadable.set(true);
      typing.set(false);
      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");
      verify(tmux, never())
          .openTaskWindow(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
      windowUnreadable.set(false);

      sessions.startWaitingFreshWhenFree("ABC-1");
      sessions.startWaitingFreshWhenFree("ABC-1");

      verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
    }
  }

  @Test
  void freshStartForRoundCarriesTheOldConversation() throws Exception {
    state.putTask("ABC-1", reviewPending);
    when(agentRuntime.sessionLogOf(root)).thenReturn(Optional.of(Path.of("/logs/old.jsonl")));
    when(agentRuntime.conversationTail(root, 16_000, Set.of(AgentWindows.NUDGE)))
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
    try (FakeLogCapture log = new FakeLogCapture(FreshStarts.class)) {

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
}
