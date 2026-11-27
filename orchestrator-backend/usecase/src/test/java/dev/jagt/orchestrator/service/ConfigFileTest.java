package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestProjectConfigBuilder.projectConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigFile.AgentConfig;
import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.master.MasterRight;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

class ConfigFileTest {

  @Test
  void readsEverySectionTheHumanActuallyWrote() {
    String json =
        """
        {
          "viewer": { "session": "alt", "viewMode": "tab-per-task", "keepViewer": false },
          "agent": { "model": "acme-model" }
        }
        """;

    ConfigFile config = new JsonMapper().readValue(json, ConfigFile.class);

    assertThat(config.viewer().session()).isEqualTo("alt");
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

  static Stream<Arguments> probeIntervals() {
    return Stream.of(Arguments.of(null, 600), Arguments.of(300, 300));
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
    assertThat(ArbiterConfig.defaults().withMode("judge").may(right)).isFalse();
  }

  @Test
  void readsAnUnsetMasterModeAsOffWhereNoRuntimeDecidedTheDefault() {
    assertThat(ArbiterConfig.defaults().modeOrDefault()).isEqualTo(MasterMode.OFF);
  }

  @Test
  void keepsApprovingPlanTheHumansWhileNoStepIsNamedAsTheirs() {
    assertThat(ArbiterConfig.defaults().withMode("act").may(MasterRight.PLAN)).isFalse();
  }

  @Test
  void keepsBothPlansAndTheDeployTheHumansWhenNoStepIsNamedAsTheirs() {
    assertThat(ArbiterConfig.defaults().mineOrDefault())
        .containsExactly("plan", "replan", "deploy", "revert");
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,24", "1,1", "48,48"},
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
      value = {"null,10", "1,1", "30,30"},
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
      value = {"null,null,60", "null,300,300", "null,100,100", "100,300,300", "100,100,100"},
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
      value = {"null,null,60", "100,null,100", "10,30,30", "10,10,10"},
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
      value = {"null,10", "1,1", "5,5"},
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
      value = {"null,20", "1,1", "7,7"},
      nullValues = "null")
  void resolvesHowManyUnattendedMovesTaskMayMakeAnHour(Integer configured, int expected) {
    assertThat(ConfigFile.LoopBreakerConfig.defaults().withPerHour(configured).perHourOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,20971520", "1,1048576", "5,5242880"},
      nullValues = "null")
  void resolvesHowManyBytesTheArchiveMayHold(Integer configured, long expected) {
    assertThat(ConfigFile.ArchiveConfig.defaults().withMaxMegabytes(configured).maxBytesOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,30", "1,1", "12,12"},
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
  void repliesToPeopleWaitForTheHumanUnlessWhereTheHumanSwitchedItOn() {
    assertThat(ConfigFile.CodeReviewConfig.defaults().repliesToPeopleUnaskedOrDefault()).isFalse();
    assertThat(
            new ConfigFile.CodeReviewConfig(null, null, null, true, null)
                .repliesToPeopleUnaskedOrDefault())
        .isTrue();
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
  void sharedReviewBotsAreEmptyWhereNoneIsWrittenAndTheWrittenOnesElse() {
    assertThat(ConfigFile.CodeReviewConfig.defaults().reviewBotsOrEmpty()).isEmpty();
    assertThat(
            new ConfigFile.CodeReviewConfig(null, null, null, null, List.of("scanner"))
                .reviewBotsOrEmpty())
        .containsExactly("scanner");
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
    assertThat(ArbiterConfig.defaults().withBrief("my-brief.md").briefOrDefault())
        .isEqualTo("my-brief.md");
    assertThat(ArbiterConfig.defaults().withBrief(" ").briefOrDefault())
        .isEqualTo("roles/master/brief.md");
  }

  @Test
  void visualizeIsOffUnlessTheHumanTurnedItOn() {
    assertThat(AgentConfig.defaults().visualizeOrDefault().enabledOrDefault()).isFalse();
    assertThat(ConfigFile.VisualizeConfig.defaults().withEnabled(true).enabledOrDefault()).isTrue();
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,3", "1,1", "19,19"},
      nullValues = "null")
  void resolvesHowManyRoundsOfOneStepMayRunBeforeTheTaskStops(Integer configured, int expected) {
    assertThat(ConfigFile.LoopBreakerConfig.defaults().withRounds(configured).roundsOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,3", "1,1", "19,19"},
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
      value = {"null,4", "1,1", "9,9"},
      nullValues = "null")
  void resolvesHowManyShipsMayGoRoundBeforeTheTaskStops(Integer configured, int expected) {
    assertThat(ConfigFile.LoopBreakerConfig.defaults().withShips(configured).shipsOrDefault())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,20", "1,1", "45,45"},
      nullValues = "null")
  void resolvesHowManyMinutesConflictMayStandStill(Integer configured, long expectedMinutes) {
    assertThat(ConfigFile.defaults().withConflictStallMinutes(configured).conflictStallOrDefault())
        .isEqualTo(java.time.Duration.ofMinutes(expectedMinutes));
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,50", "1,1", "10,10"},
      nullValues = "null")
  void resolvesHowManyTasksMayBeOpen(Integer configured, int expected) {
    assertThat(
            ConfigFile.defaults()
                .withSessions(new ConfigFile.Sessions(null, null, null, null, null, configured))
                .maxTasksOrDefault())
        .isEqualTo(expected);
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

  @ParameterizedTest
  @CsvSource({",PT0.7S", "2s,PT2S", "0,PT0S", "nonsense,PT0.7S"})
  void resolvesHowLongNudgeSitsBeforeEnterIsPressedAgain(String written, Duration expected) {
    AgentConfig agent =
        AgentConfig.defaults()
            .withCadence(new AgentConfig.Cadence(null, null, null, written, null));
    assertThat(agent.submitWaitOrDefault()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({",PT1S", "3s,PT3S", "0,PT0S", "nonsense,PT1S"})
  void resolvesHowOftenNudgeLooksAtWindowTheHumanTypesIn(String written, Duration expected) {
    AgentConfig agent =
        AgentConfig.defaults()
            .withCadence(new AgentConfig.Cadence(null, null, null, null, written));
    assertThat(agent.draftPollOrDefault()).isEqualTo(expected);
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
                    new dev.jagt.orchestrator.task.repo.ProjectConfig(
                        new dev.jagt.orchestrator.task.repo.ProjectConfig.Repository(
                            "/repo", List.of(), null, List.of(), null, null, null),
                        new dev.jagt.orchestrator.task.repo.ProjectConfig.Branches(
                            "origin/main", "dev", null),
                        new dev.jagt.orchestrator.task.repo.ProjectConfig.CodeHost(
                            own,
                            null,
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            dev.jagt.orchestrator.task.repo.ProjectConfig.Review.NONE),
                        new dev.jagt.orchestrator.task.repo.ProjectConfig.Verify(List.of(), null),
                        new dev.jagt.orchestrator.task.repo.ProjectConfig.Setup(
                            List.of(), null, List.of(), List.of()))));

    assertThat(config.hasNoCodeHost("demo")).isEqualTo(none);
  }

  @Test
  void hasNoCodeHostWhereTheProjectHasNoRemote() {
    ConfigFile config =
        ConfigFile.defaults()
            .withCodeHost("gitlab")
            .withProjects(
                java.util.Map.of(
                    "demo", projectConfig().withBaseBranch("main").withRemote("none").build()));

    assertThat(config.hasNoCodeHost("demo")).isTrue();
  }
}
