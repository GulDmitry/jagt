package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.job.Jobs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RunCommandTest {

    private final Jobs jobs = mock(Jobs.class);
    private final RunCommand command = new RunCommand(jobs);

    @Test
    void asksForTheJobItNamesToRunNow() {
        command.run(" intake ");

        verify(jobs).runNow("intake");
    }

    @Test
    void refusesARunThatNamesNoJob() {
        assertThatThrownBy(() -> command.run(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage: run <job>");
        verifyNoInteractions(jobs);
    }
}
