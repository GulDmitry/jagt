package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterShipTest {

    private final WorktreeChanges changes = mock(WorktreeChanges.class);
    private final CommandService commands = mock(CommandService.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final ConfigService config = mock(ConfigService.class);

    @Test
    void opensATaskThroughTheLineAHumanWouldType() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("demo", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(launcher.launchLine("demo drop the old keys from main"))
                .thenReturn(Launched.created("drop-the-old-keys", "drop-the-old-keys started"));

        String opened = new MasterShip(changes, commands, launcher, config).open("demo drop the old keys from main");

        assertThat(opened).isEqualTo("drop-the-old-keys started");
    }

    @Test
    void opensNoTaskForWorkOnTheDeployBranch() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("demo", new ProjectConfig("/p", "origin/main", "dev", List.of()))));

        String opened = new MasterShip(changes, commands, launcher, config).open("demo merge ABC-1 into dev");

        assertThat(opened).contains("deploy");
        verify(launcher, never()).launchLine(anyString());
    }

    @Test
    void opensATaskWhoseWordsMentionTheDeployBranchOutsideABranchPosition() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("demo", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(launcher.launchLine("demo fix the dev server port from main"))
                .thenReturn(Launched.created("fix-the-dev-server-port", "fix-the-dev-server-port started"));

        String opened = new MasterShip(changes, commands, launcher, config)
                .open("demo fix the dev server port from main");

        assertThat(opened).isEqualTo("fix-the-dev-server-port started");
    }

    @Test
    void shipsATaskThatHoldsWork() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.anyToShip(task)).thenReturn(true);

        boolean shipped = new MasterShip(changes, commands, launcher, config).ship("ABC-1", task);

        assertThat(shipped).isTrue();
        verify(commands).execute("ABC-1", TaskAction.SHIP);
    }

    @Test
    void leavesATaskHoldingNothingToShipForTheHumanToClose() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();

        boolean shipped = new MasterShip(changes, commands, launcher, config).ship("ABC-1", task);

        assertThat(shipped).isFalse();
        verify(commands, never()).execute(anyString(), any());
    }
}
