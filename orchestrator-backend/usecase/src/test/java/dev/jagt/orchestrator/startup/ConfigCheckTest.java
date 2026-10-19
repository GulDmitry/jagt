package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConfigCheckTest {

    @Test
    void acceptsAProjectThatPointsAtARealRepository(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("repo").resolve(".git"));
        Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  viewer: { tmuxSession: jagt, viewMode: tab-per-task }
                  projects:
                    demo: { path: "%s", baseBranch: origin/main, deployBranch: dev }
                """.formatted(root.resolve("repo")));
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString())
                .withStateFile(root.resolve("state.json").toString());

        assertThat(new ConfigCheck(new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class)),
                new OrchestratorPaths(properties), mock(SessionHost.class)).problems()).isEmpty();
    }

    @Test
    void saysWhereTheConfigFileShouldBeWhenThereIsNone(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString())
                .withStateFile(root.resolve("state.json").toString());

        assertThat(new ConfigCheck(new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class)),
                new OrchestratorPaths(properties), mock(SessionHost.class)).problems())
                .extracting(Problem::key)
                .containsExactly("orchestrator.config-file");
    }

    @ParameterizedTest
    @MethodSource
    void namesAKeyNothingReadsAnyMoreSoAnOldConfigDoesNotGoOnPromisingIt(String key, String expected,
                                                                        @TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("repo").resolve(".git"));
        Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  %s
                  projects:
                    demo: { path: "%s", baseBranch: origin/main, deployBranch: dev }
                """.formatted(key, root.resolve("repo")));
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString())
                .withStateFile(root.resolve("state.json").toString());

        assertThat(new ConfigCheck(new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class)),
                new OrchestratorPaths(properties), mock(SessionHost.class)).problems())
                .extracting(Problem::key)
                .containsExactly(expected);
    }

    static Stream<Arguments> namesAKeyNothingReadsAnyMoreSoAnOldConfigDoesNotGoOnPromisingIt() {
        return Stream.of(
                Arguments.of("ui: tui", "orchestrator.ui"),
                Arguments.of("terminal: warp", "orchestrator.terminal"),
                Arguments.of("openWarpWindow: false", "orchestrator.openWarpWindow"),
                Arguments.of("webTerminal: { enabled: true }", "orchestrator.webTerminal"),
                Arguments.of("dashboard: { refreshSeconds: 10 }", "orchestrator.dashboard"));
    }

    @ParameterizedTest
    @MethodSource
    void namesWhatAConfiguredProjectWouldFailOn(String config, String expected, @TempDir Path root)
            throws Exception {
        Files.createDirectories(root.resolve("repo").resolve(".git"));
        Files.createDirectories(root.resolve("plain-dir"));
        Files.writeString(root.resolve("jagt.yml"), config.replace("$ROOT", root.toString()));
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString())
                .withStateFile(root.resolve("state.json").toString());

        assertThat(new ConfigCheck(new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class)),
                new OrchestratorPaths(properties), mock(SessionHost.class)).problems())
                .extracting(Problem::key)
                .containsExactly(expected);
    }

    static Stream<Arguments> namesWhatAConfiguredProjectWouldFailOn() {
        return Stream.of(
                Arguments.of("""
                        orchestrator:
                          projects: {}
                        """, "orchestrator.projects"),
                Arguments.of("""
                        orchestrator:
                          projects: { demo: { path: "", baseBranch: origin/main } }
                        """, "orchestrator.projects.demo.path"),
                Arguments.of("""
                        orchestrator:
                          projects: { demo: { path: $ROOT/gone, baseBranch: origin/main } }
                        """, "orchestrator.projects.demo.path"),
                Arguments.of("""
                        orchestrator:
                          projects: { demo: { path: $ROOT/plain-dir, baseBranch: origin/main } }
                        """, "orchestrator.projects.demo.path"),
                Arguments.of("""
                        orchestrator:
                          projects: { demo: { path: $ROOT/repo } }
                        """, "orchestrator.projects.demo.baseBranch"),
                Arguments.of("""
                        orchestrator:
                          projects:
                            demo: { path: $ROOT/repo, baseBranch: origin/main, deployBranch: main }
                        """, "orchestrator.projects.demo.deployBranch"),
                Arguments.of("""
                        orchestrator:
                          viewer: { viewMode: one-per-task }
                          projects: { demo: { path: $ROOT/repo, baseBranch: origin/main } }
                        """, "orchestrator.viewer.viewMode"));
    }
}
