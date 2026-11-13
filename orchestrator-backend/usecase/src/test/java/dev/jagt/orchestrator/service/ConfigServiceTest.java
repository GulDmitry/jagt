package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestProjectConfigBuilder.projectConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ConfigServiceTest {

  @TempDir Path root;

  private final Checkers checkers = new Checkers(new TaskChart(Composition.builtIn()));
  private OrchestratorPaths paths;
  private ActStops stops;
  private ConfigService service;

  @BeforeEach
  void wireService() {
    paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString())
                .withStateFile(root.resolve("state.json").toString()));
    stops = new ActStops(new MasterBrake(paths));
    service = new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class), checkers);
  }

  @Test
  void readsEverySectionTheHumanActuallyWrote() {
    String json =
        """
        {
          "viewer": { "tmuxSession": "alt", "viewMode": "tab-per-task", "keepViewer": false },
          "agent": { "model": "acme-model" }
        }
        """;

    ConfigFile config = new JsonMapper().readValue(json, ConfigFile.class);

    assertThat(config.viewer().tmuxSession()).isEqualTo("alt");
    assertThat(config.viewer().sharedView()).isFalse();
    assertThat(config.viewer().keepViewerOrDefault()).isFalse();
    assertThat(config.agent().modelOrInherited()).isEqualTo("acme-model");
  }

  @Test
  void takesTheTiersTheAgentOffersWhenNoneAreWritten() {
    AgentRuntime runtime = mock(AgentRuntime.class);
    when(runtime.models()).thenReturn(List.of("opus", "sonnet"));
    when(runtime.tiers()).thenReturn(List.of("sonnet", "opus"));

    assertThat(AgentConfig.defaults().tiersFor(runtime)).containsExactly("sonnet", "opus");
  }

  @Test
  void keepsWrittenTiersTheAgentAcceptsAndDropsTheRest() {
    AgentRuntime runtime = mock(AgentRuntime.class);
    when(runtime.models()).thenReturn(List.of("opus", "sonnet"));
    when(runtime.tiers()).thenReturn(List.of("sonnet", "opus"));
    AgentConfig written =
        AgentConfig.defaults()
            .withSelection(new AgentConfig.Models(null, null, List.of("opus", "ghost")));

    assertThat(written.tiersFor(runtime)).containsExactly("opus");
  }

  @Test
  void offersNoTierWhenTheAgentCannotBeToldModel() {
    AgentRuntime runtime = mock(AgentRuntime.class);
    when(runtime.tiers()).thenReturn(List.of("sonnet", "opus"));

    assertThat(AgentConfig.defaults().tiersFor(runtime)).isEmpty();
  }

  @Test
  void answersForSectionTheConfigFileNeverMentioned() {
    ConfigFile config = new JsonMapper().readValue("{}", ConfigFile.class);

    assertThat(config.codeReview().mrTitlePatternOrDefault()).isEqualTo("{ticket} {title}");
    assertThat(config.worktree().copyGlobsOrDefault()).containsExactly("**/.env");
  }

  @Test
  void leavesAcceptanceOffWhereTheHumanWroteNone() {
    ConfigFile config = new JsonMapper().readValue("{}", ConfigFile.class);

    assertThat(config.role("acceptance").enabledOrDefault()).isFalse();
  }

  @Test
  void acceptanceIsOffWhereTheRolesKeyDoesNotSayOtherwise() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n");

    boolean enabled =
        service
            .load()
            .role("acceptance")
            .enabledOrDefault();

    assertThat(enabled).isFalse();
  }

  @Test
  void rolesKeyNamingNoRoleWithJobsNeverSwitchesTheSessionsDeployCheckOff()
      throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        """
        orchestrator:
          roles:
            foo:
              enabled: true
            ACCEPTANCE:
              enabled: true
        """);

    boolean any =
        service
            .anyCheckerEnabled();

    assertThat(any).isFalse();
  }

  @Test
  void jagtYmlWithTheOldMasterKeyStillActs() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n    mine: [ship]\n");

    MasterConfig master =
        service
            .load()
            .master();

    assertThat(master.modeOrDefault()).isEqualTo(MasterMode.ACT);
    assertThat(master.mineOrDefault()).containsExactly("ship");
  }

  @Test
  void theRolesKeyWinsOverTheOldMasterKey() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        """
        orchestrator:
          master:
            mode: act
          roles:
            master:
              mode: judge
              mine: [reply]
        """);

    MasterConfig master =
        service
            .load()
            .master();

    assertThat(master.modeOrDefault()).isEqualTo(MasterMode.JUDGE);
    assertThat(master.mineOrDefault()).containsExactly("reply");
  }

  @Test
  void partialRolesMasterKeepsTheMinesTheOldKeyNamed() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        """
        orchestrator:
          master:
            mine: [plan, deploy, revert]
          roles:
            master:
              mode: act
        """);

    MasterConfig master =
        service
            .load()
            .master();

    assertThat(master.mineOrDefault()).containsExactly("plan", "deploy", "revert");
    assertThat(master.modeOrDefault()).isEqualTo(MasterMode.ACT);
  }

  @Test
  void partialRolesMasterKeepsTheOldMode() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        "orchestrator:\n  master:\n    mode: judge\n  roles:\n    master:\n      mine: [reply]\n");

    MasterConfig master =
        service
            .load()
            .master();

    assertThat(master.modeOrDefault()).isEqualTo(MasterMode.JUDGE);
    assertThat(master.mineOrDefault()).containsExactly("reply");
  }

  @Test
  void readsTheMastersModeAsWrittenInTheShippedExample() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        """
        orchestrator:
          master:
            mode: off
        """);

    String mode =
        service
            .load()
            .master()
            .mode();

    assertThat(mode).isEqualTo("off");
  }

  @Test
  void readsActAsJudgeWhileActIsStopped() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");

    String mode =
        new ConfigService(paths, mock(AgentRuntime.class), stops, checkers)
            .load()
            .master()
            .mode();

    assertThat(mode).isEqualTo("judge");
  }

  @Test
  void readsAnEditToTheConfigFileOnTheNextLoad() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  master:\n    mode: off\n");
    ConfigService configService =
        service;
    configService.load();
    Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");

    String mode = configService.load().master().mode();

    assertThat(mode).isEqualTo("act");
  }

  @ParameterizedTest
  @CsvSource({"true, judge", "false, off"})
  void leavesTheMasterJudgingByDefaultOnlyWhereTheRuntimeReadsWhatTheHumanTyped(
      boolean reads, String expected) throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n");
    AgentRuntime runtime = mock(AgentRuntime.class);
    when(runtime.readsWhatTheHumanTyped()).thenReturn(reads);

    String mode =
        new ConfigService(paths, runtime, mock(ActStops.class), checkers)
            .load()
            .master()
            .mode();

    assertThat(mode).isEqualTo(expected);
  }

  @Test
  void leavesTheMasterOffForBlankModeWhereTheRuntimeCannotReadTheHuman()
      throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n  master:\n    mode: \"\"\n");

    String mode =
        service
            .load()
            .master()
            .mode();

    assertThat(mode).isEqualTo("off");
  }

  @Test
  void loadsConfigFileThatContainsComments() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        """
        orchestrator:
          # secrets
          worktree:
            copyGlobs: ["**/.env"]
          projects: {}   # none yet
        """);

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
  void resolvesHowOftenRunningSessionIsLookedAt(Integer configured, int expected) {
    AgentConfig agent = AgentConfig.defaults().withProbeSeconds(configured);

    assertThat(agent.probeSecondsOrDefault()).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(MasterRight.class)
  void grantsTheMasterNoRightWhileItOnlyJudges(MasterRight right) {
    assertThat(MasterConfig.defaults().withMode("judge").may(right)).isFalse();
  }

  @Test
  void keepsApprovingPlanTheHumansWhileNoStepIsNamedAsTheirs() {
    assertThat(MasterConfig.defaults().withMode("act").may(MasterRight.PLAN)).isFalse();
  }

  @Test
  void readsNoStopWhereNothingStopsTheMaster() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n");

    ConfigService config =
        new ConfigService(paths, mock(AgentRuntime.class), stops, checkers);

    assertThat(config.actStop()).isEmpty();
  }

  @Test
  void readsAnEmptySectionAsAnEmptyConfigurationWithItsMasterDefault()
      throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "other: 1\n");
    AgentRuntime runtime = mock(AgentRuntime.class);
    when(runtime.readsWhatTheHumanTyped()).thenReturn(true);

    ConfigFile config =
        new ConfigService(paths, runtime, stops, checkers)
            .load();

    assertThat(config.projects()).isEmpty();
    assertThat(config.master().mode()).isEqualTo("judge");
  }

  @Test
  void answersWhichProjectIsConfiguredAndRefusesAnUnknownOne() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        "orchestrator:\n  projects:\n    api: {path: /srv/api, baseBranch: origin/main}\n");
    ConfigService config =
        new ConfigService(paths, mock(AgentRuntime.class), stops, checkers);

    assertThat(config.project("api").path()).isEqualTo("/srv/api");
    assertThatThrownBy(() -> config.project("web"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown project 'web'. Known projects: [api]");
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,24", "0,24", "1,1", "48,48"},
      nullValues = "null")
  void resolvesHowManyHoursAnAutoReviewWindowStaysOpen(Integer configured, int expected) {
    assertThat(
            ConfigFile.AutoReviewConfig.defaults()
                .withWindowHours(configured)
                .windowHoursOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,10", "0,10", "1,1", "30,30"},
      nullValues = "null")
  void resolvesTheShortestWaitBetweenTwoAutoReviewPolls(Integer configured, int expected) {
    assertThat(
            ConfigFile.AutoReviewConfig.defaults()
                .withMinIntervalMinutes(configured)
                .minIntervalMinutesOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "null,null,240",
        "null,300,300",
        "null,30,240",
        "null,100,100",
        "100,300,300",
        "100,100,100"
      },
      nullValues = "null")
  void resolvesTheLongestWaitOnRequestOnlyReviewerMovesNeverBelowTheLongestPoll(
      Integer max, Integer waiting, int expected) {
    ConfigFile.AutoReviewConfig review =
        ConfigFile.AutoReviewConfig.defaults()
            .withMaxIntervalMinutes(max)
            .withWaitingMaxIntervalMinutes(waiting);

    assertThat(review.waitingMaxIntervalMinutesOrDefault()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,null,60", "100,null,100", "10,30,30", "10,9,60", "10,10,10", "100,50,100"},
      nullValues = "null")
  void resolvesTheLongestWaitBetweenTwoAutoReviewPollsNeverBelowTheShortest(
      Integer min, Integer max, int expected) {
    ConfigFile.AutoReviewConfig review =
        ConfigFile.AutoReviewConfig.defaults()
            .withMinIntervalMinutes(min)
            .withMaxIntervalMinutes(max);

    assertThat(review.maxIntervalMinutesOrDefault()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,10", "0,10", "1,1", "5,5"},
      nullValues = "null")
  void resolvesHowOftenTheTrackerIsPolled(Integer configured, int expected) {
    assertThat(
            ConfigFile.TrackerConfig.defaults()
                .withEveryMinutes(configured)
                .everyMinutesOrDefault())
        .isEqualTo(expected);
  }

  @Test
  void takesWorkFromEveryTrackerProjectWhereNoneIsNamed() {
    assertThat(ConfigFile.TrackerConfig.defaults().projectsOrAll()).isEmpty();
  }

  @Test
  void takesWorkOnlyFromTheTrackerProjectsNamedLeavingOutBlankOnes() {
    ConfigFile.TrackerConfig tracker =
        ConfigFile.TrackerConfig.defaults()
            .withProjects(java.util.Arrays.asList("ABC", " ", null, " XYZ "));

    assertThat(tracker.projectsOrAll()).containsExactly("ABC", "XYZ");
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,20", "0,20", "1,1", "7,7", "500,49"},
      nullValues = "null")
  void resolvesHowManyUnattendedMovesTaskMayMakeAnHour(Integer configured, int expected) {
    assertThat(ConfigFile.LoopBreakerConfig.defaults().withPerHour(configured).perHourOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,20971520", "0,20971520", "1,1048576", "5,5242880"},
      nullValues = "null")
  void resolvesHowManyBytesTheArchiveMayHold(Integer configured, long expected) {
    assertThat(ConfigFile.ArchiveConfig.defaults().withMaxMegabytes(configured).maxBytesOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,30", "0,30", "1,1", "12,12"},
      nullValues = "null")
  void resolvesHowManyFilesAnAutoplanReads(Integer configured, int expected) {
    assertThat(ConfigFile.AutoplanConfig.defaults().withFiles(configured).filesOrDefault())
        .isEqualTo(expected);
  }

  @Test
  void keepsTheMrTitlePatternTheHumanWroteAndFallsBackWhereItIsBlank() {
    assertThat(
            ConfigFile.CodeReviewConfig.defaults()
                .withMrTitlePattern("[{ticket}] {title}")
                .mrTitlePatternOrDefault())
        .isEqualTo("[{ticket}] {title}");
    assertThat(
            ConfigFile.CodeReviewConfig.defaults()
                .withMrTitlePattern(" ")
                .mrTitlePatternOrDefault())
        .isEqualTo("{ticket} {title}");
  }

  @Test
  void postsEveryDraftedReplyUnlessTheHumanSwitchedPostingOffOrFilteredByAuthor() {
    assertThat(ConfigFile.CodeReviewConfig.defaults().shipPostsEveryDraft()).isTrue();
    assertThat(
            ConfigFile.CodeReviewConfig.defaults()
                .withPostReviewReplies(false)
                .shipPostsEveryDraft())
        .isFalse();
    assertThat(
            ConfigFile.CodeReviewConfig.defaults()
                .withReviewReplyAuthors(List.of("alice"))
                .shipPostsEveryDraft())
        .isFalse();
  }

  @Test
  void repliesToEveryThreadAuthorWhereNoFilterIsWritten() {
    assertThat(ConfigFile.CodeReviewConfig.defaults().reviewReplyAuthorsOrEmpty()).isEmpty();
    assertThat(
            ConfigFile.CodeReviewConfig.defaults()
                .withReviewReplyAuthors(List.of("alice"))
                .reviewReplyAuthorsOrEmpty())
        .containsExactly("alice");
  }

  @Test
  void keepsTheAutoplanAndSandboxSectionsTheHumanWroteAndDefaultsTheOthers() {
    ConfigFile.AutoplanConfig autoplan = ConfigFile.AutoplanConfig.defaults().withFiles(12);
    ConfigFile.SandboxConfig sandbox = ConfigFile.SandboxConfig.defaults().withEnabled(false);

    AgentConfig agent = AgentConfig.defaults().withAutoplan(autoplan).withSandbox(sandbox);

    assertThat(agent.autoplanOrDefault()).isEqualTo(autoplan);
    assertThat(agent.sandboxOrDefault()).isEqualTo(sandbox);
    assertThat(AgentConfig.defaults().autoplanOrDefault())
        .isEqualTo(ConfigFile.AutoplanConfig.defaults());
    assertThat(AgentConfig.defaults().sandboxOrDefault())
        .isEqualTo(ConfigFile.SandboxConfig.defaults());
  }

  @Test
  void judgesByTheBriefTheHumanNamedAndOtherwiseByTheShippedOne() {
    assertThat(MasterConfig.defaults().withBrief("my-brief.md").briefOrDefault())
        .isEqualTo("my-brief.md");
    assertThat(MasterConfig.defaults().withBrief(" ").briefOrDefault())
        .isEqualTo("roles/master/brief.md");
  }

  @Test
  void visualizeIsOffUnlessTheHumanTurnedItOn() {
    assertThat(AgentConfig.defaults().visualizeOrDefault().enabledOrDefault()).isFalse();
    assertThat(ConfigFile.VisualizeConfig.defaults().withEnabled(true).enabledOrDefault()).isTrue();
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,3", "0,3", "1,1", "19,19", "25,19"},
      nullValues = "null")
  void resolvesHowManyRoundsOfOneStepMayRunBeforeTheTaskStops(Integer configured, int expected) {
    assertThat(ConfigFile.LoopBreakerConfig.defaults().withRounds(configured).roundsOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,3", "0,3", "1,1", "19,19", "25,19"},
      nullValues = "null")
  void resolvesHowManyRoundsRunningMayResolveNoThreadBeforeTheTaskStops(
      Integer configured, int expected) {
    assertThat(
            ConfigFile.LoopBreakerConfig.defaults()
                .withStalledRounds(configured)
                .stalledRoundsOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,4", "0,4", "1,1", "9,9"},
      nullValues = "null")
  void resolvesHowManyShipsMayGoRoundBeforeTheTaskStops(Integer configured, int expected) {
    assertThat(ConfigFile.LoopBreakerConfig.defaults().withShips(configured).shipsOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,20", "0,20", "1,1", "45,45"},
      nullValues = "null")
  void resolvesHowManyMinutesConflictMayStandStill(Integer configured, long expectedMinutes) {
    assertThat(ConfigFile.defaults().withConflictStallMinutes(configured).conflictStallOrDefault())
        .isEqualTo(java.time.Duration.ofMinutes(expectedMinutes));
  }

  @Test
  void keepsTheDiagnoseSectionTheHumanWroteAndDefaultsOneTheyDidNot() {
    assertThat(AgentConfig.defaults().diagnoseOrDefault().enabledOrDefault()).isFalse();
    assertThat(
            AgentConfig.defaults()
                .withDiagnose(new ConfigFile.DiagnoseConfig(true))
                .diagnoseOrDefault()
                .enabledOrDefault())
        .isTrue();
  }

  @Test
  void reachesTheDirectoriesSessionMayWriteThatExistWithTheSandboxOff()
      throws Exception {
    Path specs = Files.createDirectory(root.resolve("specs"));
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        """
        orchestrator:
          agent:
            sandbox:
              enabled: false
              allowWrite: ["%s", "%s"]
        """
            .formatted(specs, root.resolve("gone")));

    var beside =
        service
            .beside();

    assertThat(beside).containsExactly(specs.toRealPath());
  }

  @Test
  void handsTheLoopBreakerTheOwnerConfiguredAtTheMomentItIsAsked()
      throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        """
        orchestrator:
          loopBreaker:
            rounds: 5
            ships: 6
            stalledRounds: 7
        """);

    var breaker =
        service
            .current();

    assertThat(breaker.rounds()).isEqualTo(5);
    assertThat(breaker.ships()).isEqualTo(6);
    assertThat(breaker.stalledRounds()).isEqualTo(7);
  }

  @ParameterizedTest
  @CsvSource({",PT1H", "1h,PT1H", "2h,PT2H", "90m,PT1H30M", "0,PT0S", "nonsense,PT1H"})
  void resolvesHowLongAnIdleSessionKeepsItsCache(String written, Duration expected) {
    assertThat(AgentConfig.defaults().withCacheLifetime(written).cacheLifetimeOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({",PT30M", "10m,PT10M", "0,PT0S"})
  void resolvesHowStaleTheNotesMayGetAtTurnEnd(String written, Duration expected) {
    assertThat(AgentConfig.defaults().withNotesEvery(written).notesEveryOrDefault())
        .isEqualTo(expected);
  }

  @Test
  void readsTheStopThatStandsOnTheMaster() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n");
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");

    ConfigService config =
        new ConfigService(paths, mock(AgentRuntime.class), stops, checkers);

    assertThat(config.actStop()).isPresent();
  }

  @Test
  void namesTheRolesTheHumanWroteThatNoInstalledRoleAnswers() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        """
        orchestrator:
          roles:
            foo:
              enabled: true
            acceptance:
              enabled: true
        """);

    List<String> unknown =
        service
            .load()
            .unknownRoles(List.of("acceptance"));

    assertThat(unknown).containsExactly("foo");
  }

  @Test
  void theMasterWrittenTwiceIsNoUnknownRole() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        "orchestrator:\n  master:\n    mode: judge\n  roles:\n    master:\n      mine: [reply]\n");

    List<String> unknown =
        service
            .load()
            .unknownRoles(List.of("acceptance"));

    assertThat(unknown).isEmpty();
  }

  @Test
  void anAcceptanceWrittenUnderRolesIsUnknownWhereNoAcceptanceRoleIsInstalled()
      throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  roles:\n    acceptance:\n      model: haiku\n");

    List<String> unknown =
        service
            .load()
            .unknownRoles(List.of());

    assertThat(unknown).containsExactly("acceptance");
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {
        "NULL, none, true",
        "gitlab, none, true",
        "none, github, false",
        "none, NULL, true",
        "none, ' ', true",
        "NULL, NULL, false",
        "gitlab, NULL, false"
      })
  void hasNoCodeHostWhereTheProjectsOwnKeyOrElseTheOrchestratorsSaysNone(
      String root, String own, boolean none) {
    ConfigFile config =
        ConfigFile.defaults()
            .withCodeHost(root)
            .withProjects(
                java.util.Map.of(
                    "demo",
                    new dev.jagt.orchestrator.task.ProjectConfig(
                        new dev.jagt.orchestrator.task.ProjectConfig.Repository(
                            "/repo", List.of(), null, List.of()),
                        new dev.jagt.orchestrator.task.ProjectConfig.Branches(
                            "origin/main", "dev", null),
                        new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                            own, null, List.of(), List.of(), List.of(), List.of(), List.of()),
                        new dev.jagt.orchestrator.task.ProjectConfig.Verify(List.of(), null))));

    assertThat(config.hasNoCodeHost("demo")).isEqualTo(none);
  }

  @Test
  void hasNoCodeHostWhereTheProjectHasNoRemote() {
    ConfigFile config =
        ConfigFile.defaults()
            .withCodeHost("gitlab")
            .withProjects(
                java.util.Map.of(
                    "demo",
                    projectConfig().withBaseBranch("main").withRemote("none").build()));

    assertThat(config.hasNoCodeHost("demo")).isTrue();
  }

  @Test
  void writesEveryKeyOfFlatConfigFileBackAsTheSameTree() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    String written =
        """
        orchestrator:
          projects:
            api:
              path: /repo
              baseBranch: origin/main
              deployBranch: dev
              labels: [backend]
          viewer:
            tmuxSession: alt
          archive:
            maxMegabytes: 5
          agent:
            model: acme-model
            models: [acme-model, other-model]
            probeSeconds: 120
            cacheLifetime: 30m
            notesEvery: 10m
            sandbox:
              enabled: true
            autoplan:
              files: 12
          worktree:
            copyGlobs: ["**/.env"]
          roles:
            reviewer:
              enabled: true
            acceptance:
              enabled: true
          loopBreaker:
            ships: 6
          conflictStallMinutes: 15
          codeReview:
            mrTitlePattern: "{title}"
          autoReview:
            windowHours: 12
          master:
            mode: judge
          tracker:
            mode: intake
          codeHost: gitlab
          notifications:
            channels:
              desktop:
                kind: desktop
              team:
                kind: teams
                webhookEnv: TEAMS_HOOK
            points:
              deployed:
                - channel: desktop
                  title: "{title}"
                  text: "{sha}"
                - channel: team
                  text: "{alias} is live"
        """;
    Files.writeString(configFile, written);
    JsonMapper mapper =
        JsonMapper.builder()
            .changeDefaultPropertyInclusion(
                inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    ConfigFile config =
        service
            .load();

    Object expected = new Yaml().<java.util.Map<String, Object>>load(written).get("orchestrator");
    JsonNode actual = mapper.valueToTree(config);
    assertThat(actual).isEqualTo(mapper.<JsonNode>valueToTree(expected));
  }
}
