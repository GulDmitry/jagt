package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.service.ConfigService;
import org.junit.jupiter.api.Test;

class TrackerCheckTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final TrackerWorkflow workflow = mock(TrackerWorkflow.class);
  private final TrackerCheck check = new TrackerCheck(configService, workflow);

  @Test
  void saysNothingAboutTrackerDrivingNeitherEnd() {
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesModeThatIsNotOneOfTheOnesJagtHas() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(TrackerConfig.defaults().withMode("sometimes").withWorkflow("jira")));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.tracker.mode");
  }

  @Test
  void asksForNoLandedStageWhereNothingIsSetToClose() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(
                    TrackerConfig.defaults()
                        .withMode("take")
                        .withWorkflow("jira")
                        .withAssignee("jdoe")
                        .withStartStatus("In Progress")));
    when(workflow.id()).thenReturn("jira");

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void asksForNoStartingStageWhereNothingIsSetToTakeWork() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(
                    TrackerConfig.defaults()
                        .withMode("close")
                        .withWorkflow("jira")
                        .withDoneStatus("Ready for Stage")));
    when(workflow.id()).thenReturn("jira");

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void namesEveryBlankStageAtOnceRatherThanOnePerRestart() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(TrackerConfig.defaults().withMode("both").withWorkflow("jira")));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly(
            "orchestrator.tracker.assignee",
            "orchestrator.tracker.startStatus",
            "orchestrator.tracker.doneStatus");
  }

  @Test
  void asksFileTrackerForItsFileAndForNoAssignee() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(
                    TrackerConfig.defaults()
                        .withMode("take")
                        .withWorkflow("file")
                        .withStartStatus("open")));
    when(workflow.id()).thenReturn("file");

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.tracker.file");
  }

  @Test
  void acceptsFileTrackerNamingItsFile() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(
                    TrackerConfig.defaults()
                        .withMode("take")
                        .withWorkflow("file")
                        .withStartStatus("open")
                        .withFile("work.md")));
    when(workflow.id()).thenReturn("file");

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesDrivenTrackerJagtCarriesNoWorkflowFor() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withTracker(
                    new TrackerConfig(
                        "both", "none", "jdoe", "In Progress", "Ready for Stage", null)));
    when(workflow.id()).thenReturn("none");

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.tracker.workflow");
  }
}
