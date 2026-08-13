package dev.jagt.orchestrator.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleLoggingTest {

    private final ConsoleLogging consoleLogging = new ConsoleLogging();

    @ParameterizedTest
    @ValueSource(strings = {"tui", "both", "TUI"})
    void silencesTheConsoleForASurfaceThatPaintsTheTerminalItself(String ui) {
        MockEnvironment environment = new MockEnvironment().withProperty("orchestrator.ui", ui);

        consoleLogging.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.threshold.console")).isEqualTo("off");
    }

    /** A board-only run leaves the terminal free, and a server that prints nothing there looks dead. */
    @Test
    void leavesTheConsoleLoggingWhenOnlyTheBoardRuns() {
        MockEnvironment environment = new MockEnvironment().withProperty("orchestrator.ui", "web");

        consoleLogging.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.threshold.console")).isNull();
    }

    /** Lowest precedence: an explicit threshold on the command line must still win. */
    @Test
    void doesNotOverrideAThresholdTheHumanAskedFor() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("orchestrator.ui", "tui")
                .withProperty("logging.threshold.console", "DEBUG");

        consoleLogging.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.threshold.console")).isEqualTo("DEBUG");
    }
}
