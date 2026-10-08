package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackerCheckTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final TrackerWorkflow workflow = mock(TrackerWorkflow.class);
    private final TrackerCheck check = new TrackerCheck(configService, workflow);

    @Test
    void saysNothingAboutATrackerDrivingNeitherEnd() {
        when(configService.load()).thenReturn(ConfigFile.defaults());

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void refusesAModeThatIsNotOneOfTheOnesJagtHas() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(TrackerConfig.defaults().withMode("sometimes").withWorkflow("jira")));

        assertThat(check.problems()).singleElement().asString().contains("orchestrator.tracker.mode");
    }

    @Test
    void asksForNoLandedStageWhereNothingIsSetToClose() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(TrackerConfig.defaults().withMode("take").withWorkflow("jira").withAssignee("jdoe")
                        .withStartStatus("In Progress")));
        when(workflow.id()).thenReturn("jira");

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void asksForNoStartingStageWhereNothingIsSetToTakeWork() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(TrackerConfig.defaults().withMode("close").withWorkflow("jira")
                        .withDoneStatus("Ready for Stage")));
        when(workflow.id()).thenReturn("jira");

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void namesEveryBlankStageAtOnceRatherThanOnePerRestart() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(TrackerConfig.defaults().withMode("both").withWorkflow("jira")));

        assertThat(check.problems()).hasSize(3)
                .allSatisfy(problem -> assertThat(problem).startsWith("orchestrator.tracker."));
    }

    @Test
    void refusesADrivenTrackerJagtCarriesNoWorkflowFor() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withTracker(new TrackerConfig("both", "none", "jdoe", "In Progress", "Ready for Stage",
                        null)));
        when(workflow.id()).thenReturn("none");

        assertThat(check.problems()).singleElement().asString().contains("orchestrator.tracker.workflow");
    }
}
