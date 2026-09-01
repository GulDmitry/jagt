package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigServiceTest {

    @Test
    void readsEverySectionTheHumanActuallyWrote() {
        String json = """
                {
                  "viewer": { "tmuxSession": "alt", "viewMode": "tab-per-task", "keepViewer": false },
                  "agent": { "outputStyle": "acme:eng" }
                }
                """;

        ConfigFile config = new JsonMapper().readValue(json, ConfigFile.class);

        assertThat(config.viewer().tmuxSession()).isEqualTo("alt");
        assertThat(config.viewer().sharedView()).isFalse();
        assertThat(config.viewer().keepViewerOrDefault()).isFalse();
        assertThat(config.agent().outputStyleOrNull()).isEqualTo("acme:eng");
    }

    @Test
    void answersForASectionTheConfigFileNeverMentioned() {
        ConfigFile config = new JsonMapper().readValue("{}", ConfigFile.class);

        assertThat(config.codeReview().mrTitlePatternOrDefault()).isEqualTo("{ticket} {title}");
        assertThat(config.worktree().copyGlobsOrDefault()).containsExactly("**/.env");
    }

    @Test
    void loadsAConfigFileThatContainsComments(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, """
                orchestrator:
                  # secrets
                  worktree:
                    copyGlobs: ["**/.env"]
                  projects: {}   # none yet
                """);
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        ConfigService service = new ConfigService(new OrchestratorPaths(properties));

        assertThat(service.load().worktree().copyGlobsOrDefault()).containsExactly("**/.env");
    }

    static Stream<Arguments> probeIntervals() {
        return Stream.of(
                Arguments.of(null, 600),
                Arguments.of(-5, 600),
                Arguments.of(0, 600),
                Arguments.of(300, 300));
    }

    @ParameterizedTest
    @MethodSource("probeIntervals")
    void resolvesHowOftenARunningSessionIsLookedAt(Integer configured, int expected) {
        AgentConfig agent = AgentConfig.defaults().withProbeSeconds(configured);

        assertThat(agent.probeSecondsOrDefault()).isEqualTo(expected);
    }
}
