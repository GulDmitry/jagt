package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HumanPathsTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final WorktreeChanges changes = mock(WorktreeChanges.class);

    @Test
    void namesTheFirstChangedPathTheProjectKeepsForTheHuman() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of(),
                null, null, List.of("**/auth/**")));
        when(changes.paths(task)).thenReturn(Optional.of(Map.of("demo", List.of("README.md", "src/auth/Login.java"))));

        Optional<String> touched = new HumanPaths(configService, changes).touched(task);

        assertThat(touched).contains("demo/src/auth/Login.java");
    }

    @Test
    void namesNothingWhereNoChangedPathIsKept() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of(),
                null, null, List.of("**/auth/**")));
        when(changes.paths(task)).thenReturn(Optional.of(Map.of("demo", List.of("README.md"))));

        Optional<String> touched = new HumanPaths(configService, changes).touched(task);

        assertThat(touched).isEmpty();
    }

    @Test
    void keepsTheStepWithTheHumanWhereTheChangedPathsCouldNotBeRead() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of(),
                null, null, List.of("**/auth/**")));
        when(changes.paths(task)).thenReturn(Optional.empty());

        Optional<String> touched = new HumanPaths(configService, changes).touched(task);

        assertThat(touched).contains(HumanPaths.UNREAD);
    }

    @Test
    void readsNoPathWhereTheProjectKeepsNone() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));

        Optional<String> touched = new HumanPaths(configService, changes).touched(task);

        assertThat(touched).isEmpty();
        verifyNoInteractions(changes);
    }

    @Test
    void keepsTheStepWithTheHumanWhereTheTaskNamesAProjectNobodyConfigured() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
        when(configService.project("demo")).thenThrow(new IllegalArgumentException("no project demo"));

        Optional<String> touched = new HumanPaths(configService, changes).touched(task);

        assertThat(touched).contains(HumanPaths.UNREAD);
    }
}
