package dev.jagt.orchestrator.surface.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.support.EnvironmentPostProcessorApplicationListener;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleLoggingTest {

    private final ConsoleLogging consoleLogging = new ConsoleLogging();

    @ParameterizedTest
    @ValueSource(strings = {"tui", "both", "TUI"})
    void silencesTheConsoleForASurfaceThatPaintsTheTerminalItself(String ui) {
        MockEnvironment environment = new MockEnvironment().withProperty("orchestrator.ui", ui);

        consoleLogging.apply(environment);

        assertThat(environment.getProperty("logging.threshold.console")).isEqualTo("off");
    }

    /** A board-only run leaves the terminal free, and a server that prints nothing there looks dead. */
    @Test
    void leavesTheConsoleLoggingWhenOnlyTheBoardRuns() {
        MockEnvironment environment = new MockEnvironment().withProperty("orchestrator.ui", "web");

        consoleLogging.apply(environment);

        assertThat(environment.getProperty("logging.threshold.console")).isNull();
    }

    /** Lowest precedence: an explicit threshold on the command line must still win. */
    @Test
    void doesNotOverrideAThresholdTheHumanAskedFor() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("orchestrator.ui", "tui")
                .withProperty("logging.threshold.console", "DEBUG");

        consoleLogging.apply(environment);

        assertThat(environment.getProperty("logging.threshold.console")).isEqualTo("DEBUG");
    }

    /**
     * The whole thing hinges on running in the gap between two of Boot's listeners: too early and
     * {@code orchestrator.ui} has not been read from application.yml yet, too late and logging is already
     * initialised. Pin it against Boot's own numbers so an upgrade that moves them fails here.
     */
    @Test
    void runsAfterTheConfigFilesAreReadAndBeforeLoggingIsInitialised() {
        assertThat(consoleLogging.getOrder())
                .isGreaterThan(new EnvironmentPostProcessorApplicationListener().getOrder())
                .isLessThan(new LoggingApplicationListener().getOrder());
    }
}
