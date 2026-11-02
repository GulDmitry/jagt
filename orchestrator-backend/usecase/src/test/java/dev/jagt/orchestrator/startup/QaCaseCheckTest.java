package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QaCaseCheckTest {

    @Test
    void readsNothingWhereQaIsOff(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  qa:\n    enabled: false\n");
        Files.createDirectories(root.resolve("roles/qa"));
        Files.writeString(root.resolve("roles/qa/cases.md"), "## broken\n\nnot a case\n");

        assertThat(check(root).problems()).isEmpty();
    }

    @Test
    void acceptsTheShippedCasesAgainstTheShippedBriefWhereQaIsOn(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  qa:\n    enabled: true\n");
        Files.createDirectories(root.resolve("roles/qa"));
        Files.copy(Path.of("../../roles/qa/cases.md.dist"), root.resolve("roles/qa/cases.md.dist"));
        Files.createDirectories(root.resolve("roles/qa"));
        Files.copy(Path.of("../../roles/qa/brief.md.dist"), root.resolve("roles/qa/brief.md.dist"));

        assertThat(check(root).problems()).isEmpty();
    }

    @Test
    void namesACaseWhoseStepNoBriefSectionHolds(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  qa:\n    enabled: true\n");
        Files.createDirectories(root.resolve("roles/qa"));
        Files.writeString(root.resolve("roles/qa/cases.md"), "## accept\n\nstep: accept\nat: DEPLOYED\nby: jagt\n"
                + "replies: DEPLOYED; plain\nChecks.\n");
        Files.writeString(root.resolve("roles/qa/brief.md"), "## other\n\ntext\n");

        assertThat(check(root).problems()).hasSize(2);
    }

    private static QaCaseCheck check(Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ConfigService config = new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class));
        return new QaCaseCheck(new RoleFiles(paths, "qa"), config, paths);
    }
}
