package dev.jagt.orchestrator.adapter.tracker;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.task.TicketFacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JiraWorkflowTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final JiraWorkflow workflow = new JiraWorkflow(configService);

    @ParameterizedTest
    @CsvSource({"In Progress,dzmitry,true",
            "in progress,DZMITRY,true",
            "To Do,dzmitry,false",
            "In Progress,someone-else,false",
            "In Progress,,false"})
    void startsWorkOnlyOnTheConfiguredStageHeldByTheConfiguredPerson(String stage, String assignee,
                                                                     boolean starts) {
        when(configService.load()).thenReturn(ConfigFile.defaults().withTracker(new TrackerConfig("both", "jira",
                "dzmitry", "In Progress", "Ready for Stage", null)));
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTrackerStatus(stage).withAssignee(assignee);

        assertThat(workflow.startsWork(item)).isEqualTo(starts);
    }

    @Test
    void closesWorkOnTheStageTheInstallCallsDoneWhoeverHoldsTheItem() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withTracker(new TrackerConfig("both", "jira",
                "dzmitry", "In Progress", "Ready for Stage", null)));
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTrackerStatus("Ready for Stage").withAssignee("someone-else");

        assertThat(workflow.closesWork(item)).isTrue();
    }

    @Test
    void asksForTheItemsTheConfiguredPersonHoldsAtTheConfiguredStage() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withTracker(new TrackerConfig("both", "jira",
                "dzmitry", "In Progress", "Ready for Stage", null)));

        assertThat(workflow.candidateQuery())
                .isEqualTo("the Jira issues matching the JQL: assignee = \"dzmitry\" AND status ="
                        + " \"In Progress\"");
    }
}
