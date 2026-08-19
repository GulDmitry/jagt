package dev.jagt.orchestrator.service.commands;

import dev.jagt.orchestrator.service.TaskLauncher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ResumeCommandTest {

    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final ResumeCommand command = new ResumeCommand(launcher);

    @Test
    void resumesTheRequestTheUrlNames() {
        command.run("https://host/mr/42");

        verify(launcher).resume("https://host/mr/42");
    }

    /**
     * A review request names its own source and target branch, so a ticket typed beside its URL can only
     * contradict it — and the task that came out would be a branch the request does not track, which the next
     * `ship` pushes while the request keeps waiting on the other one.
     */
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
