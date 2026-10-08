package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.Launched;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoCommandTest {

    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final DoCommand command = new DoCommand(launcher);

    @Test
    void answersWithTheLaunchersSentenceWhenATaskIsCreated() {
        when(launcher.launchLine("ABC-9")).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

        assertThat(command.run("ABC-9")).isEqualTo("Started ABC-9.");
    }

    @Test
    void refusesALaunchThatCreatedNoTaskSoTheTypedLineIsKept() {
        when(launcher.launchLine("ABC-9")).thenReturn(Launched.refused("branch 'ABC-9' already exists in alpha"));

        assertThatThrownBy(() -> command.run("ABC-9"))
                .isInstanceOfSatisfying(Refusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(Refusal.Code.STATE))
                .hasMessage("branch 'ABC-9' already exists in alpha");
    }
}
