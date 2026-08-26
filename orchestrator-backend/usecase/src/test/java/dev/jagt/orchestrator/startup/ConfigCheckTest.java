package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
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
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

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

        assertThat(new ConfigCheck(new ConfigService(new OrchestratorPaths(properties)),
                new OrchestratorPaths(properties)).problems()).isEmpty();
    }

    @Test
    void saysWhereTheConfigFileShouldBeWhenThereIsNone(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString())
                .withStateFile(root.resolve("state.json").toString());

        assertThat(new ConfigCheck(new ConfigService(new OrchestratorPaths(properties)),
                new OrchestratorPaths(properties)).problems())
                .singleElement(STRING)
                .contains("jagt.yml.dist");
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

        assertThat(new ConfigCheck(new ConfigService(new OrchestratorPaths(properties)),
                new OrchestratorPaths(properties)).problems())
                .singleElement(STRING)
                .contains(expected);
    }

    static Stream<Arguments> namesWhatAConfiguredProjectWouldFailOn() {
        return Stream.of(
                Arguments.of("""
                        orchestrator:
                          projects: {}
                        """, "defines no projects"),
                Arguments.of("""
                        orchestrator:
                          projects: { demo: { path: "", baseBranch: origin/main } }
                        """, "projects.demo.path is empty"),
                Arguments.of("""
                        orchestrator:
                          projects: { demo: { path: $ROOT/gone, baseBranch: origin/main } }
                        """, "is not a directory"),
                Arguments.of("""
                        orchestrator:
                          projects: { demo: { path: $ROOT/plain-dir, baseBranch: origin/main } }
                        """, "is not a git repository"),
                Arguments.of("""
                        orchestrator:
                          projects: { demo: { path: $ROOT/repo } }
                        """, "projects.demo.baseBranch is empty"),
                Arguments.of("""
                        orchestrator:
                          projects:
                            demo: { path: $ROOT/repo, baseBranch: origin/main, deployBranch: main }
                        """, "equals the base branch 'main'"),
                Arguments.of("""
                        orchestrator:
                          viewer: { tmuxSession: "jagt:agents" }
                          projects: { demo: { path: $ROOT/repo, baseBranch: origin/main } }
                        """, "tmux reserves ':' and '.'"),
                Arguments.of("""
                        orchestrator:
                          viewer: { viewMode: one-per-task }
                          projects: { demo: { path: $ROOT/repo, baseBranch: origin/main } }
                        """, "viewer.viewMode 'one-per-task'"));
    }
}
