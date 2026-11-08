package dev.jagt.orchestrator.adapter.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.task.TicketFacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FileWorkflowTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final FileWorkflow workflow = new FileWorkflow(configService);

  @ParameterizedTest
  @CsvSource({"open,true", "OPEN,true", "done,false", "'',false"})
  void startsWorkOnlyOnTheConfiguredStartStatus(String status, boolean starts) {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(
                    TrackerConfig.defaults()
                        .withWorkflow("file")
                        .withStartStatus("open")
                        .withDoneStatus("done")));

    assertThat(workflow.startsWork(TicketFacts.defaults().withTrackerStatus(status)))
        .isEqualTo(starts);
  }

  @ParameterizedTest
  @CsvSource({"done,true", "open,false"})
  void closesWorkOnlyOnTheConfiguredDoneStatus(String status, boolean closes) {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(
                    TrackerConfig.defaults()
                        .withWorkflow("file")
                        .withStartStatus("open")
                        .withDoneStatus("done")));

    assertThat(workflow.closesWork(TicketFacts.defaults().withTrackerStatus(status)))
        .isEqualTo(closes);
  }

  @Test
  void startsNothingWhenNoStartStatusIsConfigured() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withTracker(TrackerConfig.defaults()));

    assertThat(workflow.startsWork(TicketFacts.defaults().withTrackerStatus("open"))).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"ABC-42,true", "AB1-7,true", "abc-42,false", "ABC-,false", "A-1,false"})
  void takesOnlyAnItemKeyAsAName(String name, boolean isKey) {
    assertThat(workflow.isItemKey(name)).isEqualTo(isKey);
  }
}
