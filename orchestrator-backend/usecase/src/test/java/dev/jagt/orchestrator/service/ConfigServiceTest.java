package dev.jagt.orchestrator.service;

import java.util.List;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.time.Clock;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import java.time.Duration;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths, Clock.systemDefaultZone()));

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
    void keepsApprovingAPlanTheHumansWhileNoStepIsNamedAsTheirs() {
        assertThat(MasterConfig.defaults().withMode("act").may(MasterRight.PLAN)).isFalse();
    }

    @Test
    void readsActAsJudgeWhileTodaysDailyCeilingStands(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");
        Files.writeString(root.resolve("starts-stopped"), java.time.LocalDate.now() + " 50\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths, Clock.systemDefaultZone()));

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
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths, Clock.systemDefaultZone()));

        String mode = new ConfigService(paths, mock(AgentRuntime.class), stops).load().master().mode();

        assertThat(mode).isEqualTo("act");
    }

    @Test
    void stopsUnattendedStartsOnceTheDaysCeilingIsTaken(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  maxStartsPerDay: 2\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths, Clock.systemDefaultZone()));
        ConfigService config = new ConfigService(paths, mock(AgentRuntime.class), stops);

        config.startTaken();
        boolean stoppedAfterOne = config.startsStopped();
        config.startTaken();

        assertThat(stoppedAfterOne).isFalse();
        assertThat(config.startsStopped()).isTrue();
        assertThat(config.actStop()).contains(new ActStop(ActStop.Cause.DAILY_CEILING, 2));
    }

    @Test
    void readsNoStopWhereNothingStopsTheMaster(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  projects: {}\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths, Clock.systemDefaultZone()));

        ConfigService config = new ConfigService(paths, mock(AgentRuntime.class), stops);

        assertThat(config.actStop()).isEmpty();
        assertThat(config.startsStopped()).isFalse();
    }

    @Test
    void readsAnEmptySectionAsAnEmptyConfigurationWithItsMasterDefault(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "other: 1\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.readsWhatTheHumanTyped()).thenReturn(true);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths, Clock.systemDefaultZone()));

        ConfigFile config = new ConfigService(paths, runtime, stops).load();

        assertThat(config.projects()).isEmpty();
        assertThat(config.master().mode()).isEqualTo("judge");
    }

    @Test
    void answersWhichProjectIsConfiguredAndRefusesAnUnknownOne(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  projects:\n    api: {path: /srv/api, baseBranch: origin/main}\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths), new DailyStarts(paths, Clock.systemDefaultZone()));
        ConfigService config = new ConfigService(paths, mock(AgentRuntime.class), stops);

        assertThat(config.project("api").path()).isEqualTo("/srv/api");
        assertThatThrownBy(() -> config.project("web"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown project 'web'. Known projects: [api]");
    }

    @ParameterizedTest
    @CsvSource(value = {"null,24", "0,24", "1,1", "48,48"}, nullValues = "null")
    void resolvesHowManyHoursAnAutoReviewWindowStaysOpen(Integer configured, int expected) {
        assertThat(ConfigFile.AutoReviewConfig.defaults().withWindowHours(configured).windowHoursOrDefault())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,10", "0,10", "1,1", "30,30"}, nullValues = "null")
    void resolvesTheShortestWaitBetweenTwoAutoReviewPolls(Integer configured, int expected) {
        assertThat(ConfigFile.AutoReviewConfig.defaults().withMinIntervalMinutes(configured).minIntervalMinutesOrDefault())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,null,60", "100,null,100", "10,30,30", "10,9,60", "10,10,10", "100,50,100"}, nullValues = "null")
    void resolvesTheLongestWaitBetweenTwoAutoReviewPollsNeverBelowTheShortest(Integer min, Integer max, int expected) {
        ConfigFile.AutoReviewConfig review = ConfigFile.AutoReviewConfig.defaults().withMinIntervalMinutes(min)
                .withMaxIntervalMinutes(max);

        assertThat(review.maxIntervalMinutesOrDefault()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,10", "0,10", "1,1", "5,5"}, nullValues = "null")
    void resolvesHowOftenTheTrackerIsPolled(Integer configured, int expected) {
        assertThat(ConfigFile.TrackerConfig.defaults().withEveryMinutes(configured).everyMinutesOrDefault())
                .isEqualTo(expected);
    }

    @Test
    void takesWorkFromEveryTrackerProjectWhereNoneIsNamed() {
        assertThat(ConfigFile.TrackerConfig.defaults().projectsOrAll()).isEmpty();
    }

    @Test
    void takesWorkOnlyFromTheTrackerProjectsNamedLeavingOutBlankOnes() {
        ConfigFile.TrackerConfig tracker = ConfigFile.TrackerConfig.defaults()
                .withProjects(java.util.Arrays.asList("ABC", " ", null, " XYZ "));

        assertThat(tracker.projectsOrAll()).containsExactly("ABC", "XYZ");
    }

    @ParameterizedTest
    @CsvSource(value = {"null,50", "0,50", "1,1", "7,7"}, nullValues = "null")
    void resolvesHowManyUnattendedStartsADayMayHold(Integer configured, int expected) {
        assertThat(ConfigFile.defaults().withMaxStartsPerDay(configured).maxStartsPerDayOrDefault()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,20971520", "0,20971520", "1,1048576", "5,5242880"}, nullValues = "null")
    void resolvesHowManyBytesTheArchiveMayHold(Integer configured, long expected) {
        assertThat(ConfigFile.ArchiveConfig.defaults().withMaxMegabytes(configured).maxBytesOrDefault()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,30", "0,30", "1,1", "12,12"}, nullValues = "null")
    void resolvesHowManyFilesAnAutoplanReads(Integer configured, int expected) {
        assertThat(ConfigFile.AutoplanConfig.defaults().withFiles(configured).filesOrDefault()).isEqualTo(expected);
    }

    @Test
    void keepsTheMrTitlePatternTheHumanWroteAndFallsBackWhereItIsBlank() {
        assertThat(ConfigFile.CodeReviewConfig.defaults().withMrTitlePattern("[{ticket}] {title}").mrTitlePatternOrDefault())
                .isEqualTo("[{ticket}] {title}");
        assertThat(ConfigFile.CodeReviewConfig.defaults().withMrTitlePattern(" ").mrTitlePatternOrDefault())
                .isEqualTo("{ticket} {title}");
    }

    @Test
    void postsEveryDraftedReplyUnlessTheHumanSwitchedPostingOffOrFilteredByAuthor() {
        assertThat(ConfigFile.CodeReviewConfig.defaults().shipPostsEveryDraft()).isTrue();
        assertThat(ConfigFile.CodeReviewConfig.defaults().withPostReviewReplies(false).shipPostsEveryDraft()).isFalse();
        assertThat(ConfigFile.CodeReviewConfig.defaults().withReviewReplyAuthors(List.of("alice")).shipPostsEveryDraft())
                .isFalse();
    }

    @Test
    void repliesToEveryThreadAuthorWhereNoFilterIsWritten() {
        assertThat(ConfigFile.CodeReviewConfig.defaults().reviewReplyAuthorsOrEmpty()).isEmpty();
        assertThat(ConfigFile.CodeReviewConfig.defaults().withReviewReplyAuthors(List.of("alice"))
                .reviewReplyAuthorsOrEmpty()).containsExactly("alice");
    }

    @Test
    void keepsTheAutoplanAndSandboxSectionsTheHumanWroteAndDefaultsTheOthers() {
        ConfigFile.AutoplanConfig autoplan = ConfigFile.AutoplanConfig.defaults().withFiles(12);
        ConfigFile.SandboxConfig sandbox = ConfigFile.SandboxConfig.defaults().withEnabled(false);

        AgentConfig agent = AgentConfig.defaults().withAutoplan(autoplan).withSandbox(sandbox);

        assertThat(agent.autoplanOrDefault()).isEqualTo(autoplan);
        assertThat(agent.sandboxOrDefault()).isEqualTo(sandbox);
        assertThat(AgentConfig.defaults().autoplanOrDefault()).isEqualTo(ConfigFile.AutoplanConfig.defaults());
        assertThat(AgentConfig.defaults().sandboxOrDefault()).isEqualTo(ConfigFile.SandboxConfig.defaults());
    }

    @Test
    void judgesByTheBriefTheHumanNamedAndOtherwiseByTheShippedOne() {
        assertThat(MasterConfig.defaults().withBrief("my-brief.md").briefOrDefault()).isEqualTo("my-brief.md");
        assertThat(MasterConfig.defaults().withBrief(" ").briefOrDefault()).isEqualTo("master-brief.md");
    }

    @Test
    void visualizeIsOffUnlessTheHumanTurnedItOn() {
        assertThat(AgentConfig.defaults().visualizeOrDefault().enabledOrDefault()).isFalse();
        assertThat(ConfigFile.VisualizeConfig.defaults().withEnabled(true).enabledOrDefault()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(value = {"null,3", "0,3", "1,1", "19,19", "25,19"}, nullValues = "null")
    void resolvesHowManyRoundsOfOneStepMayRunBeforeTheTaskStops(Integer configured, int expected) {
        assertThat(ConfigFile.LoopBreakerConfig.defaults().withRounds(configured).roundsOrDefault()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,3", "0,3", "1,1", "19,19", "25,19"}, nullValues = "null")
    void resolvesHowManyRoundsRunningMayResolveNoThreadBeforeTheTaskStops(Integer configured, int expected) {
        assertThat(ConfigFile.LoopBreakerConfig.defaults().withStalledRounds(configured).stalledRoundsOrDefault())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,4", "0,4", "1,1", "9,9"}, nullValues = "null")
    void resolvesHowManyShipsMayGoRoundBeforeTheTaskStops(Integer configured, int expected) {
        assertThat(ConfigFile.LoopBreakerConfig.defaults().withShips(configured).shipsOrDefault()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,20", "0,20", "1,1", "45,45"}, nullValues = "null")
    void resolvesHowManyMinutesAConflictMayStandStill(Integer configured, long expectedMinutes) {
        assertThat(ConfigFile.defaults().withConflictStallMinutes(configured).conflictStallOrDefault())
                .isEqualTo(java.time.Duration.ofMinutes(expectedMinutes));
    }

    @Test
    void keepsTheDiagnoseSectionTheHumanWroteAndDefaultsOneTheyDidNot() {
        assertThat(AgentConfig.defaults().diagnoseOrDefault().enabledOrDefault()).isFalse();
        assertThat(AgentConfig.defaults().withDiagnose(new ConfigFile.DiagnoseConfig(true)).diagnoseOrDefault()
                .enabledOrDefault()).isTrue();
    }

    @Test
    void handsTheLoopBreakerTheOwnerConfiguredAtTheMomentItIsAsked(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, """
                orchestrator:
                  loopBreaker:
                    rounds: 5
                    ships: 6
                    stalledRounds: 7
                """);
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        var breaker = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .current();

        assertThat(breaker.rounds()).isEqualTo(5);
        assertThat(breaker.ships()).isEqualTo(6);
        assertThat(breaker.stalledRounds()).isEqualTo(7);
    }

    @ParameterizedTest
    @CsvSource({",PT1H", "1h,PT1H", "2h,PT2H", "90m,PT1H30M", "0,PT0S", "nonsense,PT1H"})
    void resolvesHowLongAnIdleSessionKeepsItsCache(String written, Duration expected) {
        assertThat(AgentConfig.defaults().withCacheLifetime(written).cacheLifetimeOrDefault()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({",PT30M", "10m,PT10M", "0,PT0S"})
    void resolvesHowStaleTheNotesMayGetAtATurnEnd(String written, Duration expected) {
        assertThat(AgentConfig.defaults().withNotesEvery(written).notesEveryOrDefault()).isEqualTo(expected);
    }
}
