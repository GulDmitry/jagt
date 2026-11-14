package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.launch.NewTask;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.repo.BranchStrategy;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.NewRepo;
import dev.jagt.orchestrator.task.repo.Remote;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class TaskProvisioningTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private final NewTaskWorktrees worktrees = mock(NewTaskWorktrees.class);
  private final AgentSessions sessions = mock(AgentSessions.class);
  private StateService state;
  private TaskProvisioning provisioning;

  @BeforeEach
  void setUp() {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    provisioning = new TaskProvisioning(config, state, worktrees, sessions, Clock.systemUTC());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(worktrees.cut(any(), any(), any())).thenReturn(List.of(TestNewRepoBuilder.proj().build()));
  }

  @Test
  void rejectsUnknownModeBeforeCuttingAnything() {
    assertThatThrownBy(() ->
            provisioning.initializeTask(NewTask.builder("ABC-1", "proj").mode("bogus").build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown mode");
    verifyNoInteractions(worktrees);
  }

  @Test
  void registersEveryProjectAndRunsTheSessionInTheFirst() {
    when(worktrees.cut(any(), any(), any()))
        .thenReturn(
            List.of(
                TestNewRepoBuilder.proj()
                    .withProject("api")
                    .withProjectPath(Path.of("/api"))
                    .withWorktree(Path.of("/ABC-7-api"))
                    .build(),
                TestNewRepoBuilder.proj()
                    .withProject("web")
                    .withProjectPath(Path.of("/web"))
                    .withWorktree(Path.of("/ABC-7-web"))
                    .withPrimary(false)
                    .build()));

    String answer =
        provisioning.initializeTask(NewTask.builder("ABC-7", "api").alsoIn(List.of("web")).build());

    assertThat(answer).contains("agent running on ABC-7", "also in web");
    assertThat(state.task("ABC-7").orElseThrow().projects()).containsExactly("api", "web");
    verify(sessions).startAgent("ABC-7", "a1", Path.of("/ABC-7-api"), false);
  }

  @Test
  void bornTrustingWhatGitObeyedAsItsWorktreesWereCut() {
    List<NewRepo> cut = List.of(TestNewRepoBuilder.proj().withWorktree(Path.of("/wt")).build());
    when(worktrees.cut(any(), any(), any())).thenReturn(cut);
    when(worktrees.trust(cut))
        .thenReturn(GitTrust.of(Map.of("core.editor in /repo/.git/config", "a1")));

    provisioning.initializeTask(NewTask.builder("ABC-2", "proj").build());

    assertThat(state.task("ABC-2").orElseThrow().gitTrust())
        .isEqualTo(GitTrust.of(Map.of("core.editor in /repo/.git/config", "a1")));
  }

  @Test
  void assignsNextFreeAliasWhenTicketLetterAlreadyInUse() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/first", TaskStatus.IN_PROGRESS).alias("a1").build());
    when(worktrees.cut(any(), any(), any()))
        .thenReturn(List.of(TestNewRepoBuilder.proj().withWorktree(Path.of("/two")).build()));

    provisioning.initializeTask(NewTask.builder("ABC-2", "proj").build());

    assertThat(state.task("ABC-2").orElseThrow().alias()).isEqualTo("a2");
  }

  @Test
  void aliasesTicketStartingWithEyeWithTheDottedEyeWhateverTheMachineLocale() {
    when(worktrees.cut(any(), any(), any()))
        .thenReturn(
            List.of(TestNewRepoBuilder.proj().withWorktree(Path.of("/INF-2-proj")).build()));

    provisioning.initializeTask(NewTask.builder("INF-2", "proj").build());

    assertThat(state.task("INF-2").orElseThrow().alias()).isEqualTo("i1");
  }

  @Test
  void remembersTheBaseBranchTheHumanNamedForTheReviewRequest() {
    when(worktrees.cut(any(), any(), any()))
        .thenReturn(
            List.of(
                TestNewRepoBuilder.proj()
                    .withWorktree(Path.of("/ABC-3-proj"))
                    .withBaseBranch("feature/parent")
                    .build()));

    provisioning.initializeTask(
        NewTask.builder("ABC-3", "proj").baseBranch("origin/feature/parent").build());

    assertThat(state.task("ABC-3").orElseThrow().baseBranch()).isEqualTo("feature/parent");
  }

  @Test
  void leavesTheBaseBranchUnsetWhenTheHumanNamedNone() {
    when(worktrees.cut(any(), any(), any()))
        .thenReturn(
            List.of(TestNewRepoBuilder.proj().withWorktree(Path.of("/ABC-4-proj")).build()));

    provisioning.initializeTask(NewTask.builder("ABC-4", "proj").build());

    assertThat(state.task("ABC-4").orElseThrow().baseBranch()).isNull();
  }

  @Test
  void registersNothingWhenTheWorktreesCouldNotBeCut() {
    when(worktrees.cut(any(), any(), any()))
        .thenThrow(new IllegalStateException("provisioning failed"));

    assertThatThrownBy(() -> provisioning.initializeTask(NewTask.builder("ABC-9", "proj").build()))
        .isInstanceOf(IllegalStateException.class);

    assertThat(state.task("ABC-9")).isEmpty();
  }

  @Test
  void removesTheCutWorktreesWhenTheTaskCannotBeRegistered() {
    root.toFile().setWritable(false);
    NewTask request = NewTask.builder("ABC-1", "proj").build();

    assertThatThrownBy(() -> provisioning.initializeTask(request))
        .isInstanceOf(java.io.UncheckedIOException.class);

    verify(worktrees)
        .uncut(request, List.of(TestNewRepoBuilder.proj().build()), BranchStrategy.FRESH);
  }

  @Test
  void cutsSecondTaskOnlyOnceTheFirstIsRegistered() throws Exception {
    CountDownLatch firstCutting = new CountDownLatch(1);
    CountDownLatch secondCutting = new CountDownLatch(1);
    AtomicBoolean firstRegisteredWhenSecondCut = new AtomicBoolean();
    doAnswer(
            call -> {
              firstCutting.countDown();
              secondCutting.await(500, TimeUnit.MILLISECONDS);
              return List.of(
                  TestNewRepoBuilder.proj()
                      .withWorktree(Path.of("/one"))
                      .withBaseBranch("main")
                      .build());
            })
        .when(worktrees)
        .cut(argThat(task -> task.taskId().equals("ABC-1")), any(), any());
    doAnswer(
            call -> {
              firstRegisteredWhenSecondCut.set(state.task("ABC-1").isPresent());
              secondCutting.countDown();
              return List.of(
                  TestNewRepoBuilder.proj()
                      .withWorktree(Path.of("/two"))
                      .withBaseBranch("main")
                      .build());
            })
        .when(worktrees)
        .cut(argThat(task -> task.taskId().equals("ABC-2")), any(), any());
    Thread first =
        Thread.ofVirtual()
            .start(() -> provisioning.initializeTask(NewTask.builder("ABC-1", "proj").build()));
    firstCutting.await();

    provisioning.initializeTask(NewTask.builder("ABC-2", "proj").build());
    first.join();

    assertThat(firstRegisteredWhenSecondCut).isTrue();
  }

  @Test
  void refusesToBuildTaskWithNoProjectForItsSession() {
    assertThatThrownBy(() -> NewTask.builder("ABC-1", " ").alsoIn(List.of("web")).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs the project its session runs in");
  }

  @Test
  void tellsTheCallerWhereTheAgentRunsAndWhatItWasCutFrom() {

    String answer = provisioning.initializeTask(NewTask.builder("ABC-1", "proj").build());

    assertThat(answer).isEqualTo("ABC-1 is a1 — agent running on ABC-1 from origin/main.");
  }

  @Test
  void tellsTheCallerTheTaskWasResumedAndNotCutFromBase() {

    String answer =
        provisioning.initializeTask(
            NewTask.builder("ABC-1", "proj").branchStrategy("resume").build());

    assertThat(answer).isEqualTo("ABC-1 is a1 — agent running on ABC-1 (resumed).");
  }

  @Test
  void tellsTheCallerToApproveThePlanOnTheBoardOfTaskStartedInPlanMode() {

    String answer =
        provisioning.initializeTask(NewTask.builder("ABC-1", "proj").mode("plan").build());

    assertThat(answer).endsWith(" Plan mode: read plan.md, then approve-plan a1.");
  }

  @Test
  void requiresThePlanOfTaskStartedInPlanModeWhateverAutoplanSays() {

    provisioning.initializeTask(NewTask.builder("ABC-1", "proj").mode("plan").build());

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void closesThePlanGateOfAnAutoModeTaskWhenAutoplanIsOn() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withAgent(
                    ConfigService.ConfigFile.AgentConfig.defaults()
                        .withAutoplan(
                            ConfigService.ConfigFile.AutoplanConfig.defaults().withEnabled(true))));

    provisioning.initializeTask(NewTask.builder("ABC-1", "proj").mode("auto").build());

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.CLOSED);
  }

  @Test
  void leavesThePlanGateOpenOfAnAutoModeTaskWhenAutoplanIsOff() {

    provisioning.initializeTask(NewTask.builder("ABC-1", "proj").mode("auto").build());

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void keepsTheTaskRegisteredAndSaysSoWhenItsAgentSessionFailsToStart() {
    doThrow(new IllegalStateException("no tmux"))
        .when(sessions)
        .startAgent(any(), any(), any(), anyBoolean());

    String answer = provisioning.initializeTask(NewTask.builder("ABC-1", "proj").build());

    assertThat(answer)
        .isEqualTo(
            "Task ABC-1 registered and worktree created at /ABC-1-proj, but the agent session"
                + " failed to start: no tmux Fix the cause and call open_task_tab(\"ABC-1\") — do"
                + " NOT call initialize_task again.");
    assertThat(state.task("ABC-1")).isPresent();
  }

  @ParameterizedTest
  @CsvSource(value = {"https://tracker/ABC-1,https://tracker/ABC-1", "'   ',"})
  void registersTheTicketLinkOnlyWhereOneWasGiven(String given, String registered) {

    provisioning.initializeTask(NewTask.builder("ABC-1", "proj").ticketUrl(given).build());

    assertThat(state.task("ABC-1").orElseThrow().ticketUrl()).isEqualTo(registered);
  }

  @Test
  void namesTheRemoteProjectSharesItsBranchesThrough() {
    when(config.project("proj"))
        .thenReturn(
            TestProjectConfigBuilder.projectConfig()
                .withBaseBranch("upstream/main")
                .withRemote("upstream")
                .build());

    assertThat(provisioning.remoteOf("proj")).isEqualTo(new Remote("upstream"));
  }

  @Test
  void runsPlanOnlyTaskInItsScratchFolderWithNoProject() {
    Path folder = root.resolve("scratch/ABC-8");
    when(worktrees.scratch(any(), any())).thenReturn(folder);

    String answer =
        provisioning.initializeTask(
            NewTask.unplacedBuilder("ABC-8").mode("plan").baseBranch("feature/parent").build());

    TaskState task = state.task("ABC-8").orElseThrow();
    assertThat(answer).contains("agent planning in " + folder, "approve-plan a1");
    assertThat(task.baseBranch()).isEqualTo("feature/parent");
    assertThat(task.unplaced()).isTrue();
    assertThat(task.worktreePath()).isEqualTo(folder.toString());
    verify(sessions).startAgent("ABC-8", "a1", folder, true);
  }
}
