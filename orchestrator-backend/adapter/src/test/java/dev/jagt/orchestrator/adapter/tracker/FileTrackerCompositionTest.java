package dev.jagt.orchestrator.adapter.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.port.TrackerAssistant;
import dev.jagt.orchestrator.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FileTrackerCompositionTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean("claude", TrackerAssistant.class, () -> mock(TrackerAssistant.class))
          .withBean(ConfigService.class, () -> mock(ConfigService.class))
          .withUserConfiguration(FileTrackerAssistant.class)
          .withPropertyValues("orchestrator.tracker.workflow=file");

  @Test
  void theFileDriverIsTheOneAssistantAndWrapsTheOtherOne() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(TrackerAssistant.class))
              .isInstanceOf(FileTrackerAssistant.class);
        });
  }

  @Test
  void theFileDriverIsAbsentUnlessTheWorkflowIsFile() {
    runner
        .withPropertyValues("orchestrator.tracker.workflow=jira")
        .run(context -> assertThat(context).doesNotHaveBean(FileTrackerAssistant.class));
  }
}
