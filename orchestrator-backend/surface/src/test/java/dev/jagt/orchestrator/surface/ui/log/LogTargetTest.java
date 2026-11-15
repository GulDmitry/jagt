package dev.jagt.orchestrator.surface.ui.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.support.EnvironmentPostProcessorApplicationListener;
import org.springframework.mock.env.MockEnvironment;

class LogTargetTest {

  private final LogTarget target = new LogTarget();

  @Test
  void switchesToConsoleLoggingWhenLoggingIsConsole() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("orchestrator.logging", "console");

    target.apply(environment);

    assertThat(environment.getActiveProfiles()).containsExactly(LogTarget.CONSOLE_PROFILE);
  }

  @Test
  void keepsFileLoggingWhenLoggingIsUnset() {
    MockEnvironment environment = new MockEnvironment();

    target.apply(environment);

    assertThat(environment.getActiveProfiles()).isEmpty();
  }

  @Test
  void keepsFileLoggingWhenLoggingIsFile() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("orchestrator.logging", "file");

    target.apply(environment);

    assertThat(environment.getActiveProfiles()).isEmpty();
  }

  @Test
  void refusesTheStartNamingTheKeyWhenLoggingIsUnknown() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("orchestrator.logging", "syslog");

    assertThatThrownBy(() -> target.apply(environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("orchestrator.logging")
        .hasMessageContaining("syslog");
  }

  @Test
  void rollsNoLogFileWhenLoggingIsConsole() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("logging.file.name", "/nowhere/jagt.log");
    environment.addActiveProfile(LogTarget.CONSOLE_PROFILE);

    assertThat(new LogFileRoll(java.time.Clock.systemUTC(), System.err).apply(environment))
        .isFalse();
  }

  @Test
  void runsAfterTheConfigFilesAreReadAndBeforeTheLogFileRoll() {
    assertThat(target.getOrder())
        .isGreaterThan(new EnvironmentPostProcessorApplicationListener().getOrder())
        .isLessThan(new LoggingApplicationListener().getOrder());
  }
}
