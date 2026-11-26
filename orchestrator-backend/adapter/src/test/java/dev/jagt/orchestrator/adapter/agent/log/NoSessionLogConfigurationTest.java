package dev.jagt.orchestrator.adapter.agent.log;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.agent.SessionLog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NoSessionLogConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              ClaudeSessionLog.class,
              CodexSessionLog.class,
              UnpricedModels.class,
              NoSessionLogConfiguration.class);

  @Test
  void claudeLogIsWiredWhenNoCliIsConfigured() {
    runner.run(context ->
        assertThat(context.getBean(SessionLog.class)).isInstanceOf(ClaudeSessionLog.class));
  }

  @Test
  void codexLogIsWiredWhenCodexIsConfigured() {
    runner
        .withPropertyValues("orchestrator.agent.cli=codex")
        .run(context ->
            assertThat(context.getBean(SessionLog.class)).isInstanceOf(CodexSessionLog.class));
  }

  @Test
  void noSessionLogIsWiredWhenTheCliHasNoDriver() {
    runner
        .withPropertyValues("orchestrator.agent.cli=opencode")
        .run(context ->
            assertThat(context.getBean(SessionLog.class)).isInstanceOf(NoSessionLog.class));
  }
}
