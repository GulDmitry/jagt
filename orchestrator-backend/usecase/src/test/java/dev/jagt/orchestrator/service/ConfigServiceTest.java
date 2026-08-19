package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.DashboardConfig;
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
                  "dashboard": { "refreshSeconds": 30 },
                  "agent": { "outputStyle": "acme:eng" }
                }
                """;

        ConfigFile config = new JsonMapper().readValue(json, ConfigFile.class);

        assertThat(config.viewer().tmuxSession()).isEqualTo("alt");
        assertThat(config.viewer().sharedView()).isFalse();
        assertThat(config.viewer().keepViewerOrDefault()).isFalse();
        assertThat(config.dashboard().refreshSecondsOrDefault()).isEqualTo(30);
        assertThat(config.agent().outputStyleOrNull()).isEqualTo("acme:eng");
    }

    /** A whole section may be omitted, and callers must never have to null-check the one that was. */
    @Test
    void answersForASectionTheConfigFileNeverMentioned() {
        ConfigFile config = new JsonMapper().readValue("{}", ConfigFile.class);

        assertThat(config.codeReview().mrTitlePatternOrDefault()).isEqualTo("{ticket} {title}");
        assertThat(config.worktree().copyGlobsOrDefault()).containsExactly("**/.env");
    }

    @Test
    void loadsAConfigFileThatContainsComments(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("config.json");
        Files.writeString(configFile, """
                {
                  // secrets
                  "worktree": { "copyGlobs": ["**/.env"] },
                  "projects": {} // none yet
                }
                """);
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        ConfigService service = new ConfigService(new OrchestratorPaths(properties));

        assertThat(service.load().worktree().copyGlobsOrDefault()).containsExactly("**/.env");
    }

    static Stream<Arguments> intervals() {
        return Stream.of(
                Arguments.of(null, 10),
                Arguments.of(-5, 0),
                Arguments.of(0, 0),
                Arguments.of(30, 30));
    }

    @ParameterizedTest
    @MethodSource("intervals")
    void resolvesTheDashboardRefreshInterval(Integer configured, int expected) {
        DashboardConfig dashboard = DashboardConfig.defaults().withRefreshSeconds(configured);

        assertThat(dashboard.refreshSecondsOrDefault()).isEqualTo(expected);
    }

    static Stream<Arguments> reservedRows() {
        return Stream.of(
                Arguments.of(null, 17),
                Arguments.of(-3, 0),
                Arguments.of(0, 0),
                Arguments.of(25, 25));
    }

    @ParameterizedTest
    @MethodSource("reservedRows")
    void resolvesTheDashboardReservedRows(Integer configured, int expected) {
        DashboardConfig dashboard = DashboardConfig.defaults().withReservedRows(configured);

        assertThat(dashboard.reservedRowsOrDefault()).isEqualTo(expected);
    }
}
