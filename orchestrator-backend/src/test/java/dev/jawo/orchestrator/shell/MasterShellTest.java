package dev.jawo.orchestrator.shell;

import dev.jawo.orchestrator.assistant.MasterAssistant.TicketFacts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MasterShellTest {

    @Test
    void matchesTheProjectWhoseLabelIsAmongTheTicketLabels() {
        TicketFacts facts = new TicketFacts(true, "Some ticket title", "ABC", List.of("area-x", "no-test", "backend"));
        Map<String, List<String>> projectLabels = Map.of("group-a", List.of("backend"), "group-b", List.of("frontend"));

        List<String> matches = MasterShell.projectsMatching(facts, projectLabels);

        assertThat(matches).containsExactly("group-a");
    }
}
