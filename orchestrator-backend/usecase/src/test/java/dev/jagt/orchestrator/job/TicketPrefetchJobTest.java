package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerAssistant;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.KeptTicketTexts;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TicketTexts;
import dev.jagt.orchestrator.service.UsageLedger;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class TicketPrefetchJobTest {

  @TempDir static Path root;

  private final StateService state = mock(StateService.class);
  private final ConfigService config = mock(ConfigService.class);
  private final TrackerAssistant assistant = mock(TrackerAssistant.class);
  private final OrchestratorPaths paths =
      new OrchestratorPaths(
          OrchestratorProperties.defaults()
              .withRoot(root.toString())
              .withStateFile(root.resolve("state.json").toString()));
  private final TicketTexts tickets =
      new TicketTexts(
          assistant,
          new UsageTracker(state, new UsageLedger(new JsonMapper(), paths, Clock.systemDefaultZone())),
          new KeptTicketTexts(paths));
  private final TicketPrefetchJob job = new TicketPrefetchJob(config, tickets);

  @Test
  void holdsAWorkingTasksTicketForItsNextReview() {
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-42",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                    .ticketUrl("https://tracker.example/ABC-42")
                    .build()));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
    when(assistant.readTicketText("https://tracker.example/ABC-42"))
        .thenReturn(new Answer<>(Optional.of("Summary: accept v3 calls"), TokenUsage.NONE));

    job.runOn(state.tasks());

    assertThat(tickets.of("ABC-42")).contains("Summary: accept v3 calls");
  }

  @Test
  void asksOncePerStintEvenWhereTheReadFailed() {
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-43",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                    .ticketUrl("https://tracker.example/ABC-43")
                    .build()));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
    when(assistant.readTicketText("https://tracker.example/ABC-43"))
        .thenReturn(Answer.unavailable());

    job.runOn(state.tasks());
    job.runOn(state.tasks());

    verify(assistant, times(1)).readTicketText("https://tracker.example/ABC-43");
  }

  @Test
  void readsNothingWhereNoMasterWillReview() {
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-44",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                    .ticketUrl("https://tracker.example/ABC-44")
                    .build()));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

    job.runOn(state.tasks());

    verify(assistant, never()).readTicketText("https://tracker.example/ABC-44");
  }

  @Test
  void readsNoTicketAgainAfterARestartOrASecondEntry() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .ticketUrl("https://tracker.example/ABC-45")
            .build();
    when(state.tasks()).thenReturn(Map.of("ABC-45", working));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
    when(assistant.readTicketText("https://tracker.example/ABC-45"))
        .thenReturn(new Answer<>(Optional.of("Summary: accept v3 calls"), TokenUsage.NONE));
    job.runOn(state.tasks());
    TrackerAssistant afterRestart = mock(TrackerAssistant.class);
    TicketTexts restartedTickets =
        new TicketTexts(
            afterRestart,
            new UsageTracker(
                state, new UsageLedger(new JsonMapper(), paths, Clock.systemDefaultZone())),
            new KeptTicketTexts(paths));

    new TicketPrefetchJob(config, restartedTickets).runOn(state.tasks());

    verify(afterRestart, never()).readTicketText("https://tracker.example/ABC-45");
    assertThat(restartedTickets.of("ABC-45")).contains("Summary: accept v3 calls");
  }
}
