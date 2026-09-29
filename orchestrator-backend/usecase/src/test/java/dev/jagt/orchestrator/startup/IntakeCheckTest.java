package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.IntakeConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntakeCheckTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final TrackerWorkflow workflow = mock(TrackerWorkflow.class);
    private final IntakeCheck check = new IntakeCheck(configService, workflow);

    @Test
    void saysNothingAboutAnIntakeNobodyTurnedOn() {
        when(configService.load()).thenReturn(ConfigFile.defaults());

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void namesEveryBlankStageAtOnceRatherThanOnePerRestart() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withIntake(new IntakeConfig(true, "jira", null, null, null, null)));

        assertThat(check.problems()).hasSize(3)
                .allSatisfy(problem -> assertThat(problem).startsWith("orchestrator.intake."));
    }

    @Test
    void refusesIntakeTurnedOnAgainstATrackerJagtCarriesNoWorkflowFor() {
        when(configService.load()).thenReturn(ConfigFile.defaults()
                .withIntake(new IntakeConfig(true, "none", "dzmitry", "In Progress", "Ready for Stage", null)));
        when(workflow.id()).thenReturn("none");

        assertThat(check.problems()).singleElement().asString()
                .contains("orchestrator.intake.tracker");
    }
}
