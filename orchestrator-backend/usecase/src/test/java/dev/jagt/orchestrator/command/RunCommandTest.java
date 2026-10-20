package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.job.Jobs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RunCommandTest {

    private final Jobs jobs = mock(Jobs.class);
    private final RunCommand command = new RunCommand(jobs);

    @Test
    void asksForTheJobItNamesToRunNow() {
        String answer = command.run(" intake ");

        verify(jobs).runNow("intake");
        assertThat(answer).isEqualTo("intake runs within a second; `jobs` shows when it ran");
    }

    @Test
    void refusesARunThatNamesNoJob() {
        assertThatThrownBy(() -> command.run(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage: run <job>");
        verifyNoInteractions(jobs);
    }
}
