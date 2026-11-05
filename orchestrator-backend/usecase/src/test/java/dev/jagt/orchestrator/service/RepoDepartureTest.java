package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoDepartureTest {

    private final ConfigService config = mock(ConfigService.class);
    private final RetiredWorktrees worktrees = mock(RetiredWorktrees.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final RepoDeparture departure = new RepoDeparture(config, worktrees, sessions);
    private final TaskRepo web = new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");

    @Test
    void removesTheCheckoutOfARepositoryThatLeftTheTask() {
        Map<String, ProjectConfig> projects = Map.of("web", new ProjectConfig("/srv/web", "origin/main", "dev", List.of()));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(projects));

        departure.settle("ABC-42", web, "its request was closed on the host");

        verify(worktrees).remove("ABC-42", List.of(web), projects);
    }

    @Test
    void tellsTheSessionWhichRepositoryLeftAndWhy() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        departure.settle("ABC-42", web, "its request was closed on the host");

        verify(sessions).relayUnder(eq("ABC-42"), contains("web"), anyBoolean());
        verify(sessions).relayUnder(eq("ABC-42"), contains("its request was closed on the host"), anyBoolean());
    }

    @Test
    void stillTellsTheSessionWhereTheCheckoutCouldNotBeRemoved() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        doThrow(new IllegalStateException("busy")).when(worktrees).remove(any(), any(), any());

        departure.settle("ABC-42", web, "its request was closed on the host");

        verify(sessions).relayUnder(eq("ABC-42"), contains("web"), anyBoolean());
    }

    @Test
    void asksTheRetiredWorktreesWhichTrustedEntriesOnlyTheLeavingRepositoryHolds() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.PLAN_PENDING).build();
        when(worktrees.trustedOnlyBy("ABC-42", task, web)).thenReturn(Set.of("core.bare in /web/.git/config"));

        assertThat(departure.trustedOnlyBy("ABC-42", task, web)).containsExactly("core.bare in /web/.git/config");
    }
}
