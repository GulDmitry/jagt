package dev.jawo.orchestrator.shell;

import dev.jawo.orchestrator.assistant.MasterAssistant.TicketFacts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MasterShellTest {

    @Test
    void matchesTheProjectWhoseLabelIsAmongTheTicketLabels() {
        TicketFacts facts = new TicketFacts(true, "RCS - initial module API", "PAN", List.of("BE", "NoTesting", "SNG"));
        Map<String, List<String>> projectLabels = Map.of("sng", List.of("SNG"), "sobrado", List.of("PHP"));

        List<String> matches = MasterShell.projectsMatching(facts, projectLabels);

        assertThat(matches).containsExactly("sng");
    }
}
