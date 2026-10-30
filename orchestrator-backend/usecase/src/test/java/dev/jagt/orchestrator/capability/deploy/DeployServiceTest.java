package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.DeployStep;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployServiceTest {


    @Test
    void reportsAFullDeployAsDoneAndStampsTheBranchItLandedOn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.stamp()).isEqualTo("deployed to dev");
    }

    @Test
    void aBlockedDeployWithNothingLandedNamesTheObstacleInsteadOfAdvisingTheSameRunAgain(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenThrow(
                new GitDeploy.ForeignDeployWorktreeException(Path.of("/repos/ABC-1-deploy"), Path.of("/repos/x")));
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.message()).contains("/repos/ABC-1-deploy")
                .doesNotContain("Run `deploy ABC-1` again");
    }

    @Test
    void recordsTheMergeCommitTheDeployCreatedSoItCanBeReverted(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(state.task("ABC-1").orElseThrow().deployment().targets().getFirst().merge())
                .isEqualTo("cafebabe1234");
        assertThat(outcome.message()).contains("cafebabe");
    }

    @Test
    void recordsTheLandingOnTheDeploymentBesideTheMergeCommitField(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        deploys.deploy("a1");

        Deployment deployment = state.task("ABC-1").orElseThrow().deployment();
        assertThat(deployment.at()).isEqualTo(1);
        assertThat(deployment.targets().getFirst().merge()).isEqualTo("cafebabe1234");
        assertThat(deployment.targets().getFirst().deployBranch()).isEqualTo("dev");
    }

    @Test
    void recordsAFixPushedAfterTheMergeAsTheTipAndKeepsTheMergeForRevert(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).alias("a1")
                .deployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("cafebabe1234").withConflict(Conflict.of(Deployment.Cause.MERGE, 1))), 0, null)).build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/repo"), "ABC-1")).thenReturn(true);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("f1xf1x5678");
        when(git.isFix(any(), eq("f1xf1x5678"))).thenReturn(true);
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        deploys.deploy("a1");

        Deployment.Target landed = state.task("ABC-1").orElseThrow().deployment().targets().getFirst();
        assertThat(landed.merge()).isEqualTo("cafebabe1234");
        assertThat(landed.tip()).isEqualTo("f1xf1x5678");
    }

    @Test
    void refusesAPressWhileTheDeployWaitsOnTheSessionsFixAndLeavesTheStatusAlone(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).alias("a1")
                .message("red pipeline on the deploy commit: proj cafebabe")
                .deployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("cafebabe1234")
                        .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1))), 0, null)).build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/repo"), "ABC-1")).thenReturn(true);
        when(git.deployResolved(Path.of("/repo"), "ABC-1", "dev")).thenReturn(false);
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        assertThatThrownBy(() -> deploys.deploy("a1")).isInstanceOf(Refusal.class)
                .hasMessageContaining("waits on the session's fix in /ABC-1-deploy");

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
        assertThat(state.task("ABC-1").orElseThrow().message()).startsWith("red pipeline on the deploy commit");
        verify(git, never()).mergeIntoAndPush(any(), anyString(), anyString());
    }

    @Test
    void handsBackAConflictWithoutOpeningAnEditorOrTouchingTheTaskBranch(@TempDir Path root)
            throws Exception {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        Path worktree = java.nio.file.Files.createDirectories(root.resolve("wt"));
        state.putTask("ABC-1", TaskState.builder("proj", worktree.toString(), TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        GitDeploy git = mock(GitDeploy.class);
        Path deployWorktree = root.resolve("ABC-1-deploy");
        doThrow(new GitDeploy.MergeConflictException("ABC-1", "dev", "conflict in schema.yaml", deployWorktree))
                .when(git).mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"));
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(worktree.resolve("task_context.md")).doesNotExist();
        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.CONFLICT);
        assertThat(outcome.message()).contains(deployWorktree.toString())
                .contains("nothing pushed").contains("deploy ABC-1").doesNotContain("Live on the deploy branch");
        assertThat(outcome.stamp()).isEqualTo("resolve conflict in " + deployWorktree);
    }

    @Test
    void refusesToLandATaskOntoTheVeryBranchItWasCutFrom(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/release", "release", null));
        GitDeploy git = mock(GitDeploy.class);
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("base branch")
                .extracting("code").isEqualTo(Refusal.Code.STATE);
        verifyNoInteractions(git);
    }

    @Test
    void refusesToGuessWhereToLandAProjectThatNamesNoDeployBranch(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", null, null));
        GitDeploy git = mock(GitDeploy.class);
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        assertThatThrownBy(() -> deploys.deploy("ABC-1"))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("deployBranch")
                .extracting("code").isEqualTo(Refusal.Code.STATE);
    }

    @Test
    void landsEveryRepositoryATaskSpansInTheOrderItHoldsThem(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.message()).contains("api into dev (cafebabe)", "web into dev (f00dfeed)", "DEPLOYED");
        assertThat(state.task("ABC-1").orElseThrow().deployment().targets())
                .extracting(Deployment.Target::merge).containsExactly("cafebabe1234", "f00dfeed5678");
    }

    @Test
    void namesWhatIsLiveAndWhatIsNotWhenARepositoryConflictsAfterAnotherHasLanded(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        Path deployWorktree = root.resolve("ABC-1-deploy");
        doThrow(new GitDeploy.MergeConflictException("ABC-1", "dev", "conflict in Widget.java", deployWorktree))
                .when(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.CONFLICT);
        assertThat(outcome.message()).contains("CONFLICT merging web into dev", "Live on the deploy branch: api",
                "NOT deployed: web", deployWorktree.toString());
        assertThat(outcome.stamp()).contains("Live on the deploy branch: api", "NOT deployed: web");
        assertThat(state.task("ABC-1").orElseThrow().deployment().targets())
                .extracting(Deployment.Target::merge).containsExactly("cafebabe1234", null);
    }

    @Test
    void countsNothingAsLiveWhenTheFirstRepositoryOfAFreshRoundConflicts(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.REVIEWED).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployment(new Deployment(1, List.of(Deployment.Target.of("api", "dev").withMerge("0ldc0mm1t111"), Deployment.Target.of("web", "dev").withMerge("0ldc0mm1t222")), 1, null)));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        doThrow(new GitDeploy.MergeConflictException("ABC-1", "dev", "conflict", root.resolve("ABC-1-deploy")))
                .when(git).mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.message()).contains("Live on the deploy branch: none", "NOT deployed: api, web");
    }

    @Test
    void startsFromTheTopWhenADeployWorktreeIsLeftOverFromSomeRoundOtherThanAConflict(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        deploys.deploy("a1");

        verify(git).mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");
        verify(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
    }

    @Test
    void aRepeatedDeployPicksUpAtTheRepositoryTheConflictLeftBehind(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        state.updateTask("ABC-1", t -> t.withDeployment(new Deployment(1, List.of(Deployment.Target.of("api", "dev").withMerge("cafebabe1234"), Deployment.Target.of("web", "dev").withConflict(Conflict.of(Deployment.Cause.MERGE, 1))), 1, null)));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", new ProjectConfig("/repo/api", "origin/main", "dev", null), "web", new ProjectConfig("/repo/web", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);
        when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        verify(git, never()).mergeIntoAndPush(eq(Path.of("/repo/api")), anyString(), anyString());
        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.message()).contains("web into dev (f00dfeed)", "already on the deploy branch: api (cafebabe)");
    }

    @Test
    void refusesAPressWhenTheRecordedConflictsDeployWorktreeIsGoneInsteadOfMergingTheFirstRepositoryAgain(
            @TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState pressed = TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT).alias("a1").build();
        TaskState opened = TaskReducer.deployStepped(pressed, new DeployStep.Opened(List.of(
                Deployment.Target.of("api", "dev"), Deployment.Target.of("web", "dev"))), 1L);
        TaskState pushed = TaskReducer.deployStepped(opened, new DeployStep.Pushed("api", 2L), 2L);
        state.putTask("ABC-1", TaskReducer.deployStepped(pushed, new DeployStep.Conflicted("web", Deployment.Cause.MERGE), 3L));
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/repo/api", "origin/main", "dev", null),
                "web", new ProjectConfig("/repo/web", "origin/main", "dev", null))));
        GitDeploy git = mock(GitDeploy.class);
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        assertThatThrownBy(() -> deploys.deploy("a1")).isInstanceOf(Refusal.class)
                .hasMessageContaining("/repo/ABC-1-deploy");

        verify(git, never()).mergeIntoAndPush(any(), anyString(), anyString());
    }

    @Test
    void landsTheRepositoriesThatHaveWorkAndPassesOverTheOnesWithNothingToDeploy(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        doThrow(new GitDeploy.NothingToDeployException("ABC-1", "dev"))
                .when(git).mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");
        when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
        assertThat(outcome.message()).contains("web into dev (f00dfeed)", "nothing to deploy in api", "DEPLOYED")
                .doesNotContain("api into dev");
    }

    @Test
    void refusesTheDeployWhenNoRepositoryHasAnythingToDeploy(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        doThrow(new GitDeploy.NothingToDeployException("ABC-1", "dev"))
                .when(git).mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"));
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(GitDeploy.NothingToDeployException.class)
                .hasMessageContaining("Nothing to deploy");
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.APPROVED);
    }

    @Test
    void reportsAndRecordsWhatIsLiveWhenADeployBreaksOffForAReasonNoWorktreeCanFix(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        doThrow(new IllegalStateException("Deploy push to dev was rejected"))
                .when(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
        assertThat(outcome.message()).contains("Live on the deploy branch: api", "NOT deployed: web",
                "Deploy push to dev was rejected");
        assertThat(outcome.stamp()).contains("Live on the deploy branch: api", "NOT deployed: web");
        assertThat(outcome.cause()).hasMessage("Deploy push to dev was rejected");
    }

    @Test
    void leavesNoDanglingWordWhenTheFailureItReportsCarriesNoMessage(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", "dev", null));
        GitDeploy git = mock(GitDeploy.class);
        when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
        doThrow(new NullPointerException()).when(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        Outcome outcome = deploys.deploy("a1");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
        assertThat(outcome.message()).endsWith("NOT deployed: web.");
    }

    @Test
    void refusesTheWholeDeployWhenAnyRepositoryHasNoDeployBranch(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                TaskRepo.of("web", "/web-wt")), TaskStatus.APPROVED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("api")).thenReturn(new ProjectConfig("/repo/api", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/repo/web", "origin/main", null, null));
        GitDeploy git = mock(GitDeploy.class);
        DeployTargets targets = new DeployTargets(config, git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        assertThatThrownBy(() -> deploys.deploy("a1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("web")
                .hasMessageContaining("deployBranch");
        verifyNoInteractions(git);
    }

    @Test
    void refusesATaskThatIsNotThereNamingIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        GitDeploy git = mock(GitDeploy.class);
        DeployTargets targets = new DeployTargets(mock(ConfigService.class), git);
        DeployService deploys = new DeployService(state, targets, git,
                new DeployRunner(state, targets, git, Clock.systemUTC()));

        assertThatThrownBy(() -> deploys.deploy("ABC-9"))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("No task ABC-9")
                .extracting("code").isEqualTo(Refusal.Code.NO_SUCH_TASK);
    }
}
