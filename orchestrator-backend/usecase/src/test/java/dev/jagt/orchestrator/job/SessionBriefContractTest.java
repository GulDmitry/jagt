package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestRolesRootBuilder.rolesRoot;
import static dev.jagt.orchestrator.job.TestSessionBriefBuilder.sessionBrief;
import static dev.jagt.orchestrator.job.TestStepRelayBuilder.stepRelay;
import static dev.jagt.orchestrator.service.TestCaseDoorBuilder.caseDoor;
import static dev.jagt.orchestrator.service.TestNewRepoBuilder.proj;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.capability.ship.ShipService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.ResumeInstructions;
import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TicketScenarios;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.Remote;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SessionBriefContractTest {

  @TempDir Path root;

  private final TicketScenarios scenarios =
      new TicketScenarios(
          new RoleSettings(
              RoleDefinition.absent("acceptance"),
              mock(ConfigService.class),
              new OrchestratorPaths(OrchestratorProperties.defaults())));
  private final AgentSessions sessions = mock(AgentSessions.class);
  private StateService state;
  private CaseDoor door;

  @BeforeEach
  void openState() throws Exception {
    rolesRoot().withRoot(root).build();
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    state = stateService().withRoot(root).build();
    door =
        caseDoor()
            .withRoot(root)
            .build(
                state,
                taskStops().withRoot(root).build(state, Clock.systemUTC()),
                Clock.systemUTC());
  }

  @Test
  void deployConflictIsHandedOnlyInWorktreeTheBriefGrantsAsTheSessionsWithItsMergeExcepted()
      throws Exception {
    NewRepo repo =
        proj()
            .withProject("web")
            .withProjectPath(Path.of("/repo/web"))
            .withWorktree(Path.of("/w/ABC-42"))
            .withGitDir(Path.of("/repo/web/.git"))
            .withDeployBranch("dev")
            .build();
    final String brief = sessionBrief().withRoot(root).build(scenarios, repo, List.of(repo));
    state.putTask(
        "ABC-42", TaskState.builder("web", "/w/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());

    new ConflictHandover(
            sessions, stepRelay().withRoot(root).build(state, sessions, Clock.systemUTC()))
        .hand(
            "ABC-42",
            new DeployConflicts.WaitingConflict(
                Path.of("/repo/ABC-42-deploy"),
                false,
                new Conflict.Progress(7, 0, "d"),
                null,
                false,
                new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0)));

    ArgumentCaptor<String> relayed = ArgumentCaptor.captor();
    verify(sessions).writeTaskContext(eq("ABC-42"), relayed.capture());
    assertThat(relayed.getValue())
        .contains("/repo/ABC-42-deploy", "`git merge`")
        .doesNotContain("rebase", "cherry-pick", "--force", "--amend", "reset --hard");
    assertThat(brief.lines())
        .anySatisfy(line ->
            assertThat(line)
                .contains("/repo/ABC-42-deploy", "yours", "is not a `git merge` of yours"));
  }

  @Test
  void shipNamesOnlyWorktreesTheBriefListsAsTheSessionsAndPushesOnlyItsBranch() throws Exception {
    NewRepo web =
        proj()
            .withProject("web")
            .withProjectPath(Path.of("/repo/web"))
            .withWorktree(Path.of("/w/ABC-42"))
            .withGitDir(Path.of("/repo/web/.git"))
            .withDeployBranch("dev")
            .build();
    NewRepo api =
        proj()
            .withProject("api")
            .withProjectPath(Path.of("/repo/api"))
            .withWorktree(Path.of("/w/ABC-42-api"))
            .withGitDir(Path.of("/repo/api/.git"))
            .withDeployBranch("stage")
            .withPrimary(false)
            .build();
    final String brief = sessionBrief().withRoot(root).build(scenarios, web, List.of(web, api));
    state.putTask(
        "ABC-42",
        TaskState.builder("web", "/w/ABC-42", TaskStatus.REVIEW_PENDING)
            .title("Widget")
            .repos(List.of(TaskRepo.of("web", "/w/ABC-42"), TaskRepo.of("api", "/w/ABC-42-api")))
            .build());
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                        "api", new ProjectConfig("/repo/api", "origin/main", "stage", List.of()))));
    when(config.project("web"))
        .thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", List.of()));
    when(config.project("api"))
        .thenReturn(new ProjectConfig("/repo/api", "origin/main", "stage", List.of()));
    WorktreeChanges changes = mock(WorktreeChanges.class);
    when(changes.holdsWork(anyString(), anyString(), anyString())).thenReturn(true);

    ShipService ships =
        new ShipService(
            state,
            config,
            stepRelay().withRoot(root).build(state, sessions, Clock.systemUTC()),
            changes,
            door);

    OriginContext.as(ActionOrigin.BOARD, () -> ships.ship("ABC-42"));

    ArgumentCaptor<String> relayed = ArgumentCaptor.captor();
    verify(sessions).writeTaskContext(eq("ABC-42"), relayed.capture());
    assertThat(relayed.getValue())
        .contains(
            "web: /w/ABC-42,",
            "api: /w/ABC-42-api,",
            "Push branch ABC-42.",
            "ONE commit and ONE push PER REPOSITORY listed above")
        .doesNotContain("cherry-pick", "--amend", "reset --hard", "git merge");
    assertThat(brief)
        .contains("(your CWD): /w/ABC-42\n", "api: /w/ABC-42-api", "push only to `ABC-42`");
  }

  @Test
  void resumeAsksForTheOneRewriteTheBriefExceptsOnItsOwnBranch() throws Exception {
    NewRepo repo =
        proj()
            .withProject("web")
            .withProjectPath(Path.of("/repo/web"))
            .withWorktree(Path.of("/w/widget-fix"))
            .withGitDir(Path.of("/repo/web/.git"))
            .withDeployBranch("dev")
            .build();
    final String brief =
        sessionBrief().withRoot(root).withTask("widget-fix").build(scenarios, repo, List.of(repo));
    String instructions =
        ResumeInstructions.of("widget-fix", "https://host/mr/7", "origin/main", Remote.ORIGIN);

    assertThat(instructions)
        .contains("`git rebase --continue`", "`git push --force-with-lease origin widget-fix`")
        .doesNotContain("cherry-pick", "--amend");
    assertThat(brief).contains("`push --force-with-lease`", "push only to `widget-fix`");
  }
}
