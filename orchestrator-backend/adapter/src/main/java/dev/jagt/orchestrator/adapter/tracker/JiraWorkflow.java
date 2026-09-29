package dev.jagt.orchestrator.adapter.tracker;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Jira's workflow, in Jira's own words: JQL is what makes the search a query rather than a description, and the
 * stage names come from the install, no two Jira projects agreeing on how they spell a stage.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.tracker.workflow", havingValue = JiraWorkflow.ID)
@RequiredArgsConstructor
public class JiraWorkflow implements TrackerWorkflow {

    static final String ID = "jira";

    private final ConfigService configService;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String candidateQuery() {
        TrackerConfig intake = configService.load().tracker();
        return "the Jira issues matching the JQL: assignee = \"" + intake.assignee() + "\" AND status = \""
                + intake.startStatus() + "\"";
    }

    @Override
    public boolean startsWork(TicketFacts facts) {
        TrackerConfig intake = configService.load().tracker();
        return same(facts.trackerStatus(), intake.startStatus()) && same(facts.assignee(), intake.assignee());
    }

    @Override
    public boolean closesWork(TicketFacts facts) {
        return same(facts.trackerStatus(), configService.load().tracker().doneStatus());
    }

    /** Spelled as the tracker reports it, case aside: anything looser starts work on an item nobody meant. */
    private static boolean same(String read, String configured) {
        return configured != null && !configured.isBlank() && read != null
                && read.strip().equalsIgnoreCase(configured.strip());
    }
}
