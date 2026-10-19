package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.task.MasterRight;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void readsTheMastersModeAsWrittenInTheShippedExample(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, """
                orchestrator:
                  master:
                    mode: off
                """);
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        String mode = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class)).load().master().mode();

        assertThat(mode).isEqualTo("off");
    }

    @Test
    void readsActAsJudgeWhileActIsStopped(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");
        Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths));

        String mode = new ConfigService(paths, mock(AgentRuntime.class), stops).load().master().mode();

        assertThat(mode).isEqualTo("judge");
    }

    @Test
    void readsAnEditToTheConfigFileOnTheNextLoad(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: off\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        ConfigService configService = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class));
        configService.load();
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");

        String mode = configService.load().master().mode();

        assertThat(mode).isEqualTo("act");
    }

    @ParameterizedTest
    @CsvSource({"true, judge", "false, off"})
    void leavesTheMasterJudgingByDefaultOnlyWhereTheRuntimeReadsWhatTheHumanTyped(boolean reads, String expected,
                                                                                @TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  projects: {}\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.readsWhatTheHumanTyped()).thenReturn(reads);

        String mode = new ConfigService(new OrchestratorPaths(properties), runtime, mock(ActStops.class)).load().master().mode();

        assertThat(mode).isEqualTo(expected);
    }

    @Test
    void leavesTheMasterOffForABlankModeWhereTheRuntimeCannotReadTheHuman(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  projects: {}\n  master:\n    mode: \"\"\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        String mode = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class)).load().master().mode();

        assertThat(mode).isEqualTo("off");
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
        ConfigService service = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class));

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

    @ParameterizedTest
    @EnumSource(MasterRight.class)
    void grantsTheMasterNoRightWhileItOnlyJudges(MasterRight right) {
        assertThat(MasterConfig.defaults().withMode("judge").may(right)).isFalse();
    }

    @Test
    void readsActAsJudgeWhileTodaysDailyCeilingStands(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");
        Files.writeString(root.resolve("starts-stopped"), java.time.LocalDate.now() + " 50\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths));

        String mode = new ConfigService(paths, mock(AgentRuntime.class), stops).load().master().mode();

        assertThat(mode).isEqualTo("judge");
    }

    @Test
    void readsActAsActOnceTheDayOfTheDailyCeilingIsOver(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");
        Files.writeString(root.resolve("starts-stopped"), java.time.LocalDate.now().minusDays(1) + " 50\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths));

        String mode = new ConfigService(paths, mock(AgentRuntime.class), stops).load().master().mode();

        assertThat(mode).isEqualTo("act");
    }
}
