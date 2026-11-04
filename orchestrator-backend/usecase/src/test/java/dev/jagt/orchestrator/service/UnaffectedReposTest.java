package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnaffectedReposTest {

    private final ConfigService config = mock(ConfigService.class);
    private final GitWorktrees git = mock(GitWorktrees.class);
    private final RepoDeparture departure = mock(RepoDeparture.class);

    @Test
    void anUnaffectedRepositoryLeavesTheTaskWhereItsBranchHoldsNoOwnCommits(@TempDir Path root) throws IOException {
        StateService state = stateOf(root);
        Files.writeString(root.resolve("plan.md"), "## Repositories\n- [changed] api\n- [unaffected: only the API changes] web\n");
        TaskRepo web = new TaskRepo("web", "/web-wt", "git@host:g/web.git", null);
        state.putTask("ABC-42", TaskState.builder(List.of(new TaskRepo("api", root.toString(), "git@host:g/api.git", null), web),
                TaskStatus.PLAN_PENDING).build());
        when(config.project("web")).thenReturn(new ProjectConfig("/srv/web", "origin/main", "dev", List.of()));
        when(git.holdsOwnCommits(Path.of("/srv/web"), "ABC-42", "origin/main")).thenReturn(false);

        new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

        assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api");
        verify(departure).settle(eq("ABC-42"), eq(web), any());
    }

    @Test
    void anUnaffectedRepositoryHoldingOwnCommitsStaysOnTheTask(@TempDir Path root) throws IOException {
        StateService state = stateOf(root);
        Files.writeString(root.resolve("plan.md"), "## Repositories\n- [changed] api\n- [unaffected: only the API changes] web\n");
        state.putTask("ABC-42", TaskState.builder(List.of(new TaskRepo("api", root.toString(), "git@host:g/api.git", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", null)), TaskStatus.PLAN_PENDING).build());
        when(config.project("web")).thenReturn(new ProjectConfig("/srv/web", "origin/main", "dev", List.of()));
        when(git.holdsOwnCommits(Path.of("/srv/web"), "ABC-42", "origin/main")).thenReturn(true);

        new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

        assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
        verifyNoInteractions(departure);
    }

    @Test
    void everyRepositoryStaysWhereThePlanMarksNoneUnaffected(@TempDir Path root) throws IOException {
        StateService state = stateOf(root);
        Files.writeString(root.resolve("plan.md"), "## Repositories\n- [changed] api\n- [changed] web\n");
        state.putTask("ABC-42", TaskState.builder(List.of(new TaskRepo("api", root.toString(), "git@host:g/api.git", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", null)), TaskStatus.PLAN_PENDING).build());

        new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

        assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
        verifyNoInteractions(departure, git);
    }

    @Test
    void theLastRepositoryStaysWhereThePlanMarksEveryOneUnaffected(@TempDir Path root) throws IOException {
        StateService state = stateOf(root);
        Files.writeString(root.resolve("plan.md"),
                "## Repositories\n- [unaffected: nothing moves] api\n- [unaffected: nothing moves] web\n");
        state.putTask("ABC-42", TaskState.builder(List.of(new TaskRepo("api", root.toString(), "git@host:g/api.git", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", null)), TaskStatus.PLAN_PENDING).build());
        when(config.project(any())).thenReturn(new ProjectConfig("/srv/any", "origin/main", "dev", List.of()));

        new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

        assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("web");
        verify(departure).settle(eq("ABC-42"), any(), any());
    }

    @Test
    void nothingLeavesWhereTheWorktreeHoldsNoPlan(@TempDir Path root) {
        StateService state = stateOf(root);
        state.putTask("ABC-42", TaskState.builder(List.of(new TaskRepo("api", root.toString(), "git@host:g/api.git", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", null)), TaskStatus.PLAN_PENDING).build());

        new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

        assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    }

    private static StateService stateOf(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }
}
