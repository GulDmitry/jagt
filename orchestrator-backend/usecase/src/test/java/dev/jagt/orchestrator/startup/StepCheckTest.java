package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.port.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StepCheckTest {

    @Test
    void refusesAStepNoCaseSends(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        Files.writeString(root.resolve("session-cases.md"), "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n");

        assertThat(new StepCheck(new SessionCases(paths), new SessionBrief(paths)).problems())
                .contains(new Problem("session-cases.md", "session-cases.md: no case sends the step 'deploy' — add one, or it"
                        + " never reaches a session"));
    }

    @Test
    void refusesAStepTheBriefHoldsNoSectionFor(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        Files.writeString(root.resolve("session-brief.md"), "## wake\n\ngo on\n");
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));

        assertThat(new StepCheck(new SessionCases(paths), new SessionBrief(paths)).problems())
                .contains(new Problem("session-brief.md", "session-brief.md: no section for the step 'deploy' — add one, or"
                        + " the session is told nothing of it"));
    }

    @Test
    void passesTheShippedBriefAndCases(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));

        assertThat(new StepCheck(new SessionCases(paths), new SessionBrief(paths)).problems()).isEmpty();
    }
}
