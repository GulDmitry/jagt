package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.QaBrief;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaEnvironment;
import dev.jagt.orchestrator.task.QaVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaPromptsTest {

    @Test
    void aRunMayFetchEachEnvironmentUrlAndItsLandedProbeAndNothingElse(@TempDir Path root) {
        QaConfig qa = new QaConfig(true, null, List.of(
                new QaEnvironment("https://dev.example.test", "https://dev.example.test/version"),
                new QaEnvironment("https://stage.example.test", null)));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        QaReader.Run run = new QaPrompts(new QaBrief(paths)).accept("ABC-42", "/wt", "abc123", qa, QaVerdict.ACCEPT);

        assertThat(run.environments()).containsExactly("https://dev.example.test",
                "https://dev.example.test/version", "https://stage.example.test");
    }
}
