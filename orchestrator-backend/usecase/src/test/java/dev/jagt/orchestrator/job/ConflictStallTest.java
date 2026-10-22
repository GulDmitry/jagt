package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ConflictWatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConflictStallTest {

    private final ConfigService config = mock(ConfigService.class);
    private final Notifications notifications = mock(Notifications.class);

    @Test
    void tellsTheHumanOnceWhenAHandedConflictStopsMoving(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("web", "/wt", TaskStatus.DEPLOY_CONFLICT).lastActiveTimestamp(1_000)
                .build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        DeployConflicts.WaitingConflict conflict = new DeployConflicts.WaitingConflict(Path.of("/src/ABC-42-deploy"),
                false, new ConflictWatch.Progress(7, 0, "d"));
        new ConflictStall(state, notifications, config, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))
                .watch("ABC-42", conflict);

        new ConflictStall(state, notifications, config, Clock.fixed(Instant.ofEpochMilli(1_201_000), ZoneOffset.UTC))
                .watch("ABC-42", conflict);
        new ConflictStall(state, notifications, config, Clock.fixed(Instant.ofEpochMilli(2_401_000), ZoneOffset.UTC))
                .watch("ABC-42", conflict);

        verify(notifications, times(1)).send(any());
        assertThat(state.task("ABC-42").orElseThrow().currentConflict()).hasValueSatisfying(
                watch -> assertThat(watch.stalled()).isTrue());
    }

    @Test
    void leavesAConflictWhoseWorktreeChangedWithinTheWindowToTheSession(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("web", "/wt", TaskStatus.DEPLOY_CONFLICT).lastActiveTimestamp(1_000)
                .build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        Path worktree = Path.of("/src/ABC-42-deploy");
        new ConflictStall(state, notifications, config, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))
                .watch("ABC-42", new DeployConflicts.WaitingConflict(worktree, false, new ConflictWatch.Progress(7, 0, "a")));
        new ConflictStall(state, notifications, config, Clock.fixed(Instant.ofEpochMilli(1_141_000), ZoneOffset.UTC))
                .watch("ABC-42", new DeployConflicts.WaitingConflict(worktree, false, new ConflictWatch.Progress(7, 0, "b")));

        new ConflictStall(state, notifications, config, Clock.fixed(Instant.ofEpochMilli(1_501_000), ZoneOffset.UTC))
                .watch("ABC-42", new DeployConflicts.WaitingConflict(worktree, false, new ConflictWatch.Progress(7, 0, "b")));

        verify(notifications, never()).send(any());
    }

    @Test
    void aRestartNeitherTellsTheHumanAgainNorHandsTheConflictAgain(@TempDir Path root) {
        Path file = root.resolve("state.json");
        StateService before = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(file.toString())));
        before.putTask("ABC-42", TaskState.builder("web", "/wt", TaskStatus.DEPLOY_CONFLICT).lastActiveTimestamp(1_000)
                .build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        DeployConflicts.WaitingConflict conflict = new DeployConflicts.WaitingConflict(Path.of("/src/ABC-42-deploy"),
                false, new ConflictWatch.Progress(7, 0, "d"));
        new ConflictStall(before, notifications, config, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))
                .watch("ABC-42", conflict);
        new ConflictStall(before, notifications, config, Clock.fixed(Instant.ofEpochMilli(1_201_000), ZoneOffset.UTC))
                .watch("ABC-42", conflict);
        StateService after = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(file.toString())));
        ConflictStall restarted = new ConflictStall(after, notifications, config,
                Clock.fixed(Instant.ofEpochMilli(3_601_000), ZoneOffset.UTC));

        boolean handed = restarted.handed("ABC-42");
        restarted.watch("ABC-42", conflict);
        new ConflictStall(after, notifications, config, Clock.fixed(Instant.ofEpochMilli(4_801_000), ZoneOffset.UTC))
                .watch("ABC-42", conflict);

        verify(notifications, times(1)).send(any());
        assertThat(handed).isTrue();
    }

    @Test
    void aConflictFromANewDeployPressIsHandedAgain(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("web", "/wt", TaskStatus.DEPLOY_CONFLICT).lastActiveTimestamp(1_000)
                .build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        ConflictStall stall = new ConflictStall(state, notifications, config,
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
        stall.watch("ABC-42", new DeployConflicts.WaitingConflict(Path.of("/src/ABC-42-deploy"), false,
                new ConflictWatch.Progress(7, 0, "d")));
        state.updateTask("ABC-42", task -> task.withStatus(TaskStatus.DEPLOY_CONFLICT, "conflict again", true, 900_000));

        boolean handed = stall.handed("ABC-42");

        assertThat(handed).isFalse();
    }
}
