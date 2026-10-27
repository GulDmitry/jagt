package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.capability.ship.ShipService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.MissLog;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.ResumeRegistration;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.SubAgentBriefing;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.TicketReader;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.ConflictWatch;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionBriefContractTest {

    @Test
    void aDeployConflictIsHandedOnlyInAWorktreeTheBriefGrantsAsTheSessionsWithItsMergeExcepted(@TempDir Path root)
            throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);
        String brief = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo,
                List.of(repo), ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(), ConfigService.ConfigFile.DiagnoseConfig.defaults());
        AgentSessions sessions = mock(AgentSessions.class);

        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("web", "/w/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());

        CaseDoor door = new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC()));

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions, door,
                new TaskTurns(state, Clock.systemUTC())),
                mock(ConflictStall.class), mock(ConflictFinish.class)).handle("ABC-42",
                new DeployConflicts.WaitingConflict(Path.of("/repo/ABC-42-deploy"), false,
                        new ConflictWatch.Progress(7, 0, "d")));

        ArgumentCaptor<String> relayed = ArgumentCaptor.captor();
        verify(sessions).writeTaskContext(eq("ABC-42"), relayed.capture());
        assertThat(relayed.getValue()).contains("/repo/ABC-42-deploy", "`git merge`")
                .doesNotContain("rebase", "cherry-pick", "--force", "--amend", "reset --hard");
        assertThat(brief.lines()).anySatisfy(line -> assertThat(line)
                .contains("/repo/ABC-42-deploy", "yours", "is not a `git merge` of yours"));
    }

    @Test
    void aShipNamesOnlyWorktreesTheBriefListsAsTheSessionsAndPushesOnlyItsBranch(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo web = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);
        NewRepo api = new NewRepo("api", new ProjectConfig("/repo/api", "origin/main", "stage", List.of()),
                Path.of("/repo/api"), Path.of("/w/ABC-42-api"), Path.of("/repo/api/.git"), "origin/main",
                "git@host:g/api.git", false);
        String brief = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), web,
                List.of(web, api), ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(), ConfigService.ConfigFile.DiagnoseConfig.defaults());
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("web", "/w/ABC-42", TaskStatus.REVIEW_PENDING).title("Widget")
                .repos(List.of(TaskRepo.of("web", "/w/ABC-42"), TaskRepo.of("api", "/w/ABC-42-api"))).build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                "api", new ProjectConfig("/repo/api", "origin/main", "stage", List.of()))));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", List.of()));
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "stage", List.of()));
        WorktreeChanges changes = mock(WorktreeChanges.class);
        when(changes.holdsWork(anyString(), anyString(), anyString())).thenReturn(true);
        AgentSessions sessions = mock(AgentSessions.class);

        CaseDoor door = new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC()));

        ShipService ships = new ShipService(state, config, new StepRelay(new SessionBrief(paths), sessions, door,
                new TaskTurns(state, Clock.systemUTC())),
                changes, door);

        OriginContext.as(ActionOrigin.BOARD, () -> ships.ship("ABC-42"));

        ArgumentCaptor<String> relayed = ArgumentCaptor.captor();
        verify(sessions).writeTaskContext(eq("ABC-42"), relayed.capture());
        assertThat(relayed.getValue()).contains("web: /w/ABC-42,", "api: /w/ABC-42-api,", "Push branch ABC-42.",
                        "ONE commit and ONE push PER REPOSITORY listed above")
                .doesNotContain("rebase", "cherry-pick", "--force", "--amend", "reset --hard", "git merge");
        assertThat(brief).contains("(your CWD): /w/ABC-42\n", "api: /w/ABC-42-api", "push only to `ABC-42`");
    }

    @Test
    void aResumeAsksForTheOneRewriteTheBriefExceptsOnItsOwnBranch(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/widget-fix"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);
        String brief = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("widget-fix", "web").build(),
                repo, List.of(repo), ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(), ConfigService.ConfigFile.DiagnoseConfig.defaults());
        TaskProvisioning provisioning = mock(TaskProvisioning.class);

        new ResumeRegistration(provisioning, mock(AgentStatusReports.class), mock(TicketReader.class))
                .register("widget-fix", "web", "https://host/mr/7", "Widget", "origin/main");

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().instructions()).contains("`git rebase --continue`",
                "`git push --force-with-lease origin widget-fix`").doesNotContain("cherry-pick", "--amend");
        assertThat(brief).contains("jagt rebased your branch leaving conflicts", "`push --force-with-lease`",
                "push only to `widget-fix`");
    }
}
