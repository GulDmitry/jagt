package dev.jagt.orchestrator.adapter.tracker;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.task.TicketFacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JiraWorkflowTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final JiraWorkflow workflow = new JiraWorkflow(configService);

    @ParameterizedTest
    @CsvSource({"In Progress,jdoe,true",
            "in progress,JDOE,true",
            "To Do,jdoe,false",
            "In Progress,someone-else,false",
            "In Progress,,false"})
    void startsWorkOnlyOnTheConfiguredStageHeldByTheConfiguredPerson(String stage, String assignee,
                                                                     boolean starts) {
        when(configService.load()).thenReturn(ConfigFile.defaults().withTracker(new TrackerConfig("both", "jira",
                "jdoe", "In Progress", "Ready for Stage", null)));
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTrackerStatus(stage).withAssignee(assignee);

        assertThat(workflow.startsWork(item)).isEqualTo(starts);
    }

    @ParameterizedTest
    @CsvSource({"ABC,true", "abc,true", "XYZ,false", "'',false"})
    void startsWorkOnlyOnAnItemOfABoardTheInstallNamed(String board, boolean starts) {
        when(configService.load()).thenReturn(ConfigFile.defaults().withTracker(TrackerConfig.defaults()
                .withMode("take").withWorkflow("jira").withAssignee("jdoe").withStartStatus("In Progress")
                .withProjects(List.of("ABC"))));
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTrackerStatus("In Progress").withAssignee("jdoe").withTrackerProject(board);

        assertThat(workflow.startsWork(item)).isEqualTo(starts);
    }

    @Test
    void asksOnlyForTheBoardsTheInstallNamed() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withTracker(TrackerConfig.defaults()
                .withMode("take").withWorkflow("jira").withAssignee("jdoe").withStartStatus("In Progress")
                .withProjects(List.of("ABC", "XYZ"))));

        assertThat(workflow.candidateQuery()).endsWith(" AND project in (\"ABC\", \"XYZ\")");
    }

    @Test
    void closesWorkOnTheStageTheInstallCallsDoneWhoeverHoldsTheItem() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withTracker(new TrackerConfig("both", "jira",
                "jdoe", "In Progress", "Ready for Stage", null)));
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTrackerStatus("Ready for Stage").withAssignee("someone-else");

        assertThat(workflow.closesWork(item)).isTrue();
    }

    @Test
    void asksForTheItemsTheConfiguredPersonHoldsAtTheConfiguredStage() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withTracker(new TrackerConfig("both", "jira",
                "jdoe", "In Progress", "Ready for Stage", null)));

        assertThat(workflow.candidateQuery())
                .isEqualTo("the Jira issues matching the JQL: assignee = \"jdoe\" AND status ="
                        + " \"In Progress\"");
    }
}
