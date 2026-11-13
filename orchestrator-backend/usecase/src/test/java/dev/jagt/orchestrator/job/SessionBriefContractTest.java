package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.capability.ship.ShipService;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.MissLog;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.ResumeInstructions;
import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.StopPolicy;
import dev.jagt.orchestrator.service.SubAgentBriefing;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.TicketScenarios;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class SessionBriefContractTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final TicketScenarios scenarios =
      new TicketScenarios(
          new RoleSettings(
              RoleDefinition.absent("acceptance"),
              mock(ConfigService.class),
              new OrchestratorPaths(OrchestratorProperties.defaults())));

  @Test
  void deployConflictIsHandedOnlyInWorktreeTheBriefGrantsAsTheSessionsWithItsMergeExcepted(
      @TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);
    final String brief =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))),
                scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());
    AgentSessions sessions = mock(AgentSessions.class);

    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42", TaskState.builder("web", "/w/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());

    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));

    new ConflictHandover(
            sessions,
            new StepRelay(
                new SessionBrief(paths),
                sessions,
                door,
                new TaskTurns(
                    state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())))))
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
        .anySatisfy(
            line ->
                assertThat(line)
                    .contains("/repo/ABC-42-deploy", "yours", "is not a `git merge` of yours"));
  }

  @Test
  void shipNamesOnlyWorktreesTheBriefListsAsTheSessionsAndPushesOnlyItsBranch(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo web =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);
    NewRepo api =
        new NewRepo(
            "api",
            new ProjectConfig("/repo/api", "origin/main", "stage", List.of()),
            Path.of("/repo/api"),
            Path.of("/w/ABC-42-api"),
            Path.of("/repo/api/.git"),
            "origin/main",
            "git@host:g/api.git",
            false);
    final String brief =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))),
                scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                web,
                List.of(web, api),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
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
    AgentSessions sessions = mock(AgentSessions.class);

    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));

    ShipService ships =
        new ShipService(
            state,
            config,
            new StepRelay(
                new SessionBrief(paths),
                sessions,
                door,
                new TaskTurns(
                    state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())))),
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
  void resumeAsksForTheOneRewriteTheBriefExceptsOnItsOwnBranch(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/widget-fix"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);
    final String brief =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))),
                scenarios)
            .of(
                NewTask.builder("widget-fix", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());
    String instructions =
        ResumeInstructions.of("widget-fix", "https://host/mr/7", "origin/main", Remote.ORIGIN);

    assertThat(instructions)
        .contains("`git rebase --continue`", "`git push --force-with-lease origin widget-fix`")
        .doesNotContain("cherry-pick", "--amend");
    assertThat(brief).contains("`push --force-with-lease`", "push only to `widget-fix`");
  }
}
