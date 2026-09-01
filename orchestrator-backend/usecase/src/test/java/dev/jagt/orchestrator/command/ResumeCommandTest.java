package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.Launched;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResumeCommandTest {

    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final ResumeCommand command = new ResumeCommand(launcher);

    @Test
    void resumesTheRequestTheUrlNames() {
        when(launcher.resume("https://host/mr/42")).thenReturn(Launched.created("PROJ-1", "Resumed PROJ-1"));

        command.run("https://host/mr/42");

        verify(launcher).resume("https://host/mr/42");
    }

    @Test
    void refusesAResumeThatTriesToNameTheTaskBesideTheRequestUrl() {
        assertThatThrownBy(() -> command.run("https://host/mr/42 ABC-9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carries its own branches");
        verifyNoInteractions(launcher);
    }

    @Test
    void refusesAResumeWithNoUrlAtAll() {
        assertThatThrownBy(() -> command.run("ABC-9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage: resume <request-url>");
        verifyNoInteractions(launcher);
    }
}
