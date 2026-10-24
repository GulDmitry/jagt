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

class CaseCheckTest {

    @Test
    void refusesACaseNamingAStepTheBriefLacks(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        Files.writeString(root.resolve("session-brief.md"), "## wake\n\ngo on\n");
        Files.writeString(root.resolve("session-cases.md"), "## wake\n\nstep: wake\nat: any\nby: jagt\nA reset.\n\n"
                + "## woke\n\nstep: woke\nat: any\nby: jagt\nA typo.\n");

        assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems()).containsExactly(
                new Problem("session-cases.md", "session-cases.md: case woke names step 'woke', which no brief section holds"));
    }

    @Test
    void refusesABriefStepNoCaseSends(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        Files.writeString(root.resolve("session-brief.md"), "## wake\n\ngo on\n\n## deploy\n\ncheck it\n");
        Files.writeString(root.resolve("session-cases.md"), "## wake\n\nstep: wake\nat: any\nby: jagt\nA reset.\n");

        assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems()).containsExactly(
                new Problem("session-cases.md", "session-cases.md: no case sends the brief step 'deploy' — add one, or it"
                        + " never reaches a session"));
    }

    @Test
    void refusesACaseNamingNoStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        Files.writeString(root.resolve("session-brief.md"), "## wake\n\ngo on\n");
        Files.writeString(root.resolve("session-cases.md"), "## wake\n\nstep: wake\nat: WAITING\nby: jagt\nA reset.\n");

        assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems())
                .extracting(Problem::text).anySatisfy(text -> assertThat(text).startsWith(
                        "session-cases.md: case wake has at: 'WAITING'"));
    }

    @Test
    void passesTheShippedCasesAgainstTheShippedBrief(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));

        assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems()).isEmpty();
    }
}
