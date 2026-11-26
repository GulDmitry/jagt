package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestAgentSessionsBuilder.agentSessions;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.Untaken;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgentSessionsRelayTest {

  @TempDir Path root;

  private StateService state;
  private AgentSessions sessions;
  private TaskState inProgress;
  private final SessionHost tmux = mock(SessionHost.class);
  private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

  @BeforeEach
  void readableConfigOverAnEmptyStateFile() throws Exception {
    state = stateService().withRoot(root).build();
    sessions =
        agentSessions()
            .withRoot(root)
            .build(state, tmux, agentRuntime, mock(WorktreeChanges.class));
    when(tmux.sessionName(null)).thenReturn("jagt");
    when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));
    when(agentRuntime.displayName()).thenReturn("Claude");
    inProgress = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
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
        .thenReturn(Optional.of("❯ "))
        .thenReturn(Optional.of(left))
        .thenReturn(Optional.of("❯ "));
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
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(Optional.of("❯ "))
        .thenReturn(Optional.of(left));
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
    when(agentRuntime.draft("> nudge")).thenReturn(Optional.of(AgentWindows.NUDGE));
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
        .thenReturn(Optional.of("> where is the merge re"))
        .thenReturn(Optional.of("> "));
    when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "new instructions");

    verify(tmux, timeout(5000)).nudgeTaskWindow("jagt", "ABC-1", AgentWindows.NUDGE);
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
  void confirmsLineWasSaidToTheRunningAgent() {
    state.putTask(
        "ABC-1", TaskState.builder("demo", root.toString(), TaskStatus.REVIEW_PENDING).build());
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", "answer 2 differently")).thenReturn(true);

    assertThat(sessions.say("ABC-1", "answer 2 differently")).isEqualTo("Said to the agent.");
  }

  @Test
  void nudgesTheRunningConversationWhenItIsStillWithinItsCacheLifetime() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "Review round for http://mr/1.");

    verify(tmux, never())
        .openTaskWindow(
            anyString(), anyString(), anyString(), any(), any(), eq(LaunchMode.WORK), anyString());
  }

  @Test
  void reEntersTheSessionOfTaskWhoseWindowIsGoneRatherThanDroppingTheRelay() {
    state.putTask("ABC-1", inProgress);
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);

    assertThat(sessions.writeTaskContext("ABC-1", "new instructions")).contains("re-entered");
    verify(tmux).reviveTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any());
  }

  @Test
  void nudgesTheSessionHumanJustRestartedRatherThanStartingItFreshAgain() {
    state.putTask(
        "ABC-1",
        inProgress.withSession(
            Session.defaults().withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 5_000, null))));
    when(agentRuntime.displayName()).thenReturn("Claude");
    when(agentRuntime.pastCacheLifetime(5_000L, java.time.Duration.ofHours(1))).thenReturn(true);
    sessions.openTaskTab("ABC-1", "auto");
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "new instructions");

    verify(tmux).nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString());
    verify(tmux, times(1))
        .openTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any(), any(), anyString());
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
  void carriesAnUntakenReviewRoundBesideTheInstructionThatFollowsIt() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-42-demo"));
    state.putTask(
        "ABC-42",
        TaskState.builder("demo", worktree.toString(), TaskStatus.CI_POLLING)
            .untaken(
                new Untaken("a1b2c3d4e5f60718", List.of("105840"), "ROUND: answer note 105840"))
            .build());

    sessions.writeTaskContext("ABC-42", "SHIP: commit and push");

    assertThat(Files.readString(worktree.resolve("task_context.md")))
        .contains("SHIP: commit and push", "ROUND: answer note 105840");
  }

  @Test
  void keepsPendingInstructionAboveWhatIsRelayedUnderIt() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-42-demo"));
    Files.writeString(
        worktree.resolve("task_context.md"), "Resolve the conflict in /src/ABC-42-deploy.");
    state.putTask(
        "ABC-42",
        TaskState.builder("demo", worktree.toString(), TaskStatus.DEPLOY_CONFLICT).build());

    sessions.relayUnder("ABC-42", "Keep both imports.");

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
    sessions.relayUnder("ABC-42", "Keep both imports.");

    sessions.relayUnder("ABC-42", "Drop the old import.");

    assertThat(Files.readString(worktree.resolve("task_context.md")))
        .contains("Resolve the conflict in /src/ABC-42-deploy.", "Drop the old import.")
        .doesNotContain("Keep both imports.");
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
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", AgentWindows.NUDGE)).thenReturn(true);

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
        .thenReturn(Optional.of("> where is the merge re"))
        .thenReturn(Optional.of("> "));
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
  void tellsTheCallerTheSessionWasReenteredWhenInstructionsAreRelayedUnderPendingOne() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.DEPLOY_CONFLICT).build());

    String answer = sessions.relayUnder("ABC-1", "Keep both imports.");

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
        .openTaskWindow(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
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
    String left = "❯ " + AgentWindows.NUDGE;
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(Optional.of("❯ "))
        .thenReturn(Optional.of(left))
        .thenReturn(Optional.of(left))
        .thenReturn(Optional.of(left))
        .thenReturn(Optional.of("❯ "));
    when(agentRuntime.draft(left)).thenReturn(Optional.of(AgentWindows.NUDGE));
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", AgentWindows.NUDGE)).thenReturn(true);
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
    String left = "❯ " + AgentWindows.NUDGE;
    when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(tmux.screenOf("jagt", "ABC-1"))
        .thenReturn(Optional.of(draft))
        .thenReturn(Optional.of("> "))
        .thenReturn(Optional.of(left))
        .thenReturn(Optional.of("> "));
    when(agentRuntime.holdsDraft(draft)).thenReturn(true);
    when(agentRuntime.draft(left)).thenReturn(Optional.of(AgentWindows.NUDGE));
    when(tmux.nudgeTaskWindow("jagt", "ABC-1", AgentWindows.NUDGE)).thenReturn(true);
    when(tmux.submitTaskWindow("jagt", "ABC-1")).thenReturn(true);

    sessions.writeTaskContext("ABC-1", "new instructions");

    verify(tmux, timeout(5000)).submitTaskWindow("jagt", "ABC-1");
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
        .openTaskWindow(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
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
