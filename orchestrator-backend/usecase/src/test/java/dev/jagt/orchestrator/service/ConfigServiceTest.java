package dev.jagt.orchestrator.service;

import java.util.List;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.time.Clock;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import java.time.Duration;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.task.MasterMode;
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
    void leavesQaOffWithSonnetAndNoEnvironmentWhereTheHumanWroteNone() {
        ConfigFile config = new JsonMapper().readValue("{}", ConfigFile.class);

        assertThat(config.qa().enabledOrDefault()).isFalse();
        assertThat(config.qa().modelOrDefault()).isEqualTo("sonnet");
        assertThat(config.qa().allows("https://stage.example.com")).isFalse();
    }

    @Test
    void allowsOnlyTheEnvironmentsQaNamesWithTheirLandedProbe() {
        ConfigFile config = new JsonMapper().readValue("""
                { "qa": { "enabled": true, "environments": [
                  { "url": "https://stage.example.com", "landed": "https://stage.example.com/health" } ] } }
                """, ConfigFile.class);

        assertThat(config.qa().enabledOrDefault()).isTrue();
        assertThat(config.qa().allows("https://stage.example.com")).isTrue();
        assertThat(config.qa().allows("https://prod.example.com")).isFalse();
        assertThat(config.qa().environmentsOrEmpty().getFirst().landed()).isEqualTo("https://stage.example.com/health");
    }

    @Test
    void readsTheEventsTheHumanListedForDesktopBanners(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  notifications:\n    desktop: [stopped, deployed]\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        var events = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().notifications().desktopEvents();

        assertThat(events).containsExactlyInAnyOrder(Notification.Event.STOPPED, Notification.Event.DEPLOYED);
    }

    @Test
    void keepsEveryBannerButDeployedWhereTheHumanListedNone() {
        var events = ConfigFile.defaults().notifications().desktopEvents();

        assertThat(events).contains(Notification.Event.YOUR_MOVE, Notification.Event.REQUEST_OPENED)
                .doesNotContain(Notification.Event.DEPLOYED);
    }

    @Test
    void readsTheCommandAndEventsTheHumanListedForTheTeamChannel(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  notifications:\n    channel:\n"
                + "      command: [sh, -c, cat]\n      events: [stopped, no-such-event]\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        var channel = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().notifications().channel();

        assertThat(channel.commandOrEmpty()).containsExactly("sh", "-c", "cat");
        assertThat(channel.eventSet()).containsExactly(Notification.Event.STOPPED);
    }

    @Test
    void leavesTheTeamChannelOffAndListsARequestAndADeployWhereTheHumanWroteNone() {
        var channel = ConfigFile.defaults().notifications().channel();

        assertThat(channel.commandOrEmpty()).isEmpty();
        assertThat(channel.eventSet())
                .containsExactlyInAnyOrder(Notification.Event.REQUEST_OPENED, Notification.Event.DEPLOYED);
    }

    @Test
    void treatsABlankFirstWordAsNoCommand() {
        var channel = new ConfigFile.NotificationsConfig.ChannelConfig(List.of(" "), null);

        assertThat(channel.commandOrEmpty()).isEmpty();
    }

    @Test
    void qaIsOffWhenNeitherTheRolesKeyNorTheOldOneSaysOtherwise(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  projects: {}\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        boolean enabled = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().role("qa").enabledOrDefault();

        assertThat(enabled).isFalse();
    }

    @Test
    void aJagtYmlWithTheOldQaKeyStillTurnsQaOn(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  qa:\n    enabled: true\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        boolean enabled = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().role("qa").enabledOrDefault();

        assertThat(enabled).isTrue();
    }

    @Test
    void aRolesKeyNamingNoRoleWithJobsNeverSwitchesTheSessionsDeployCheckOff(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  roles:\n    foo:\n      enabled: true\n    QA:\n      enabled: true\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        boolean any = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().anyCheckerEnabled();

        assertThat(any).isFalse();
    }

    @Test
    void aJagtYmlWithTheOldMasterKeyStillActs(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n    mine: [ship]\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        MasterConfig master = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().master();

        assertThat(master.modeOrDefault()).isEqualTo(MasterMode.ACT);
        assertThat(master.mineOrDefault()).containsExactly("ship");
    }

    @Test
    void theRolesKeyWinsOverTheOldMasterKey(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n  roles:\n    master:\n      mode: judge\n      mine: [reply]\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        MasterConfig master = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().master();

        assertThat(master.modeOrDefault()).isEqualTo(MasterMode.JUDGE);
        assertThat(master.mineOrDefault()).containsExactly("reply");
    }

    @Test
    void aPartialRolesMasterKeepsTheMinesTheOldKeyNamed(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mine: [plan, deploy, revert]\n  roles:\n    master:\n      mode: act\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        MasterConfig master = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().master();

        assertThat(master.mineOrDefault()).containsExactly("plan", "deploy", "revert");
        assertThat(master.modeOrDefault()).isEqualTo(MasterMode.ACT);
    }

    @Test
    void aPartialRolesMasterKeepsTheOldMode(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: judge\n  roles:\n    master:\n      mine: [reply]\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        MasterConfig master = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().master();

        assertThat(master.modeOrDefault()).isEqualTo(MasterMode.JUDGE);
        assertThat(master.mineOrDefault()).containsExactly("reply");
    }

    @Test
    void aPartialRolesQaKeepsTheEnvironmentsTheOldKeyNamed(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  qa:\n    enabled: true\n    environments:\n      - url: https://dev.example\n  roles:\n    qa:\n      model: haiku\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        ConfigService.ConfigFile.RoleConfig qa = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().role("qa");

        assertThat(qa.enabledOrDefault()).isTrue();
        assertThat(qa.environmentsOrEmpty()).hasSize(1);
        assertThat(qa.model()).isEqualTo("haiku");
    }

    @Test
    void theRolesKeyWinsOverTheOldOne(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  qa:\n    enabled: true\n  roles:\n    qa:\n      enabled: false\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        boolean enabled = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().role("qa").enabledOrDefault();

        assertThat(enabled).isFalse();
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
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths));

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
    void readsActAsJudgeWhileThreeBadEndingsStandInARow(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");
        Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths));

        String mode = new ConfigService(paths, mock(AgentRuntime.class), stops).load().master().mode();

        assertThat(mode).isEqualTo("judge");
    }

    @Test
    void readsNoStopWhereNothingStopsTheMaster(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  projects: {}\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths));

        ConfigService config = new ConfigService(paths, mock(AgentRuntime.class), stops);

        assertThat(config.actStop()).isEmpty();
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
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths));

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
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths));
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
    @CsvSource(value = {"null,null,240", "null,300,300", "null,30,240", "null,100,100", "100,300,300", "100,100,100"}, nullValues = "null")
    void resolvesTheLongestWaitOnARequestOnlyAReviewerMovesNeverBelowTheLongestPoll(Integer max, Integer waiting, int expected) {
        ConfigFile.AutoReviewConfig review = ConfigFile.AutoReviewConfig.defaults().withMaxIntervalMinutes(max)
                .withWaitingMaxIntervalMinutes(waiting);

        assertThat(review.waitingMaxIntervalMinutesOrDefault()).isEqualTo(expected);
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
    @CsvSource(value = {"null,20", "0,20", "1,1", "7,7", "500,49"}, nullValues = "null")
    void resolvesHowManyUnattendedMovesATaskMayMakeAnHour(Integer configured, int expected) {
        assertThat(ConfigFile.LoopBreakerConfig.defaults().withPerHour(configured).perHourOrDefault()).isEqualTo(expected);
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
        assertThat(MasterConfig.defaults().withBrief(" ").briefOrDefault()).isEqualTo("roles/master/brief.md");
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
    void reachesTheDirectoriesASessionMayWriteThatExistWithTheSandboxOff(@TempDir Path root) throws Exception {
        Path specs = Files.createDirectory(root.resolve("specs"));
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, """
                orchestrator:
                  agent:
                    sandbox:
                      enabled: false
                      allowWrite: ["%s", "%s"]
                """.formatted(specs, root.resolve("gone")));
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        var beside = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .beside();

        assertThat(beside).containsExactly(specs.toRealPath());
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

    @Test
    void readsTheStopThatStandsOnTheMaster(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  projects: {}\n");
        Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        ActStops stops = new ActStops(new MasterBrake(mock(StateService.class), paths));

        ConfigService config = new ConfigService(paths, mock(AgentRuntime.class), stops);

        assertThat(config.actStop()).isPresent();
    }

    @Test
    void namesTheRolesTheHumanWroteThatNoInstalledRoleAnswers(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  roles:\n    foo:\n      enabled: true\n    qa:\n      enabled: true\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        List<String> unknown = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().unknownRoles(List.of("qa"));

        assertThat(unknown).containsExactly("foo");
    }

    @Test
    void theMasterWrittenTwiceIsNoUnknownRole(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  master:\n    mode: judge\n  roles:\n    master:\n      mine: [reply]\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        List<String> unknown = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().unknownRoles(List.of("qa"));

        assertThat(unknown).isEmpty();
    }

    @Test
    void theOldQaKeyAloneNamesNoRoleUnderRoles(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  qa:\n    enabled: true\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        ConfigService.ConfigFile config = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class),
                mock(ActStops.class)).load();

        assertThat(config.unknownRoles(List.of())).isEmpty();
        assertThat(config.role("qa").enabledOrDefault()).isTrue();
    }

    @Test
    void aKeyLeftBlankUnderRolesKeepsWhatTheOldKeyWrote(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  qa:\n    model: opus\n  roles:\n    qa:\n      model:\n      enabled: true\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        ConfigService.ConfigFile.RoleConfig qa = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class),
                mock(ActStops.class)).load().role("qa");

        assertThat(qa.model()).isEqualTo("opus");
        assertThat(qa.enabledOrDefault()).isTrue();
    }

    @Test
    void aQaWrittenUnderRolesIsUnknownWhereNoQaRoleIsInstalled(@TempDir Path root) throws Exception {
        Path configFile = root.resolve("jagt.yml");
        Files.writeString(configFile, "orchestrator:\n  qa:\n    enabled: true\n  roles:\n    qa:\n      model: haiku\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()).withStateFile(root.resolve("state.json").toString());

        List<String> unknown = new ConfigService(new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class))
                .load().unknownRoles(List.of());

        assertThat(unknown).containsExactly("qa");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL, none, true", "gitlab, none, true", "none, github, false",
            "none, NULL, true", "none, ' ', true", "NULL, NULL, false", "gitlab, NULL, false"})
    void hasNoCodeHostWhereTheProjectsOwnKeyOrElseTheOrchestratorsSaysNone(String root, String own, boolean none) {
        ConfigFile config = ConfigFile.defaults().withCodeHost(root).withProjects(java.util.Map.of(
                "demo", new dev.jagt.orchestrator.task.ProjectConfig("/repo", "origin/main", "dev", List.of(), List.of(),
                        null, null, own, null, null, null, null, null, null, null)));

        assertThat(config.hasNoCodeHost("demo")).isEqualTo(none);
    }

    @Test
    void hasNoCodeHostWhereTheProjectHasNoRemote() {
        ConfigFile config = ConfigFile.defaults().withCodeHost("gitlab").withProjects(java.util.Map.of(
                "demo", new dev.jagt.orchestrator.task.ProjectConfig("/repo", "main", "dev", List.of(), List.of(),
                        null, null, null, null, null, null, null, "none", null, null)));

        assertThat(config.hasNoCodeHost("demo")).isTrue();
    }
}
