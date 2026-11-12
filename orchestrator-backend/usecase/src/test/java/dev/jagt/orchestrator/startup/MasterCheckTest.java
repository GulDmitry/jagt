package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.CodeReviewConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.service.master.MasterBriefs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterCheckTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

  @TempDir Path root;

  private MasterCheck check;

  @BeforeEach
  void checkOverTheTempRoot() {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    check =
        new MasterCheck(
            configService, new MasterBriefs(paths, new SessionBrief(paths)), agentRuntime);
  }

  @Test
  void findsNothingWrongWithTheSettingEveryInstallShipsWith() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md.dist"), "the shipped standards");
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults()));
    assertThat(check.problems()).isEmpty();
  }

  @Test
  void anUnknownModeUnderTheRolesKeyIsRefusedByItsNewName() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    master:\n      mode: supervise\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    ConfigService real =
        new ConfigService(
            paths,
            agentRuntime,
            mock(dev.jagt.orchestrator.service.ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn())));

    MasterCheck realCheck =
        new MasterCheck(real, new MasterBriefs(paths, new SessionBrief(paths)), agentRuntime);

    assertThat(realCheck.problems())
        .singleElement()
        .satisfies(
            problem -> {
              assertThat(problem.key()).isEqualTo("orchestrator.roles.master.mode");
              assertThat(problem.text()).startsWith("orchestrator.roles.master.mode: 'supervise'");
            });
  }

  @Test
  void refusesModeThisMachineDoesNotHave() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("supervise")));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.mode");
  }

  @Test
  void refusesMasterThatShipsWhileEveryDraftedReplyStillWaitsForYouToPostIt()
      throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act"))
                .withCodeReview(new CodeReviewConfig(null, false, null)));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.mode");
  }

  @Test
  void refusesTheRetiredNameRatherThanSilentlyGivingTheMasterEveryStep()
      throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(
                    MasterConfig.defaults().withMode("act").withWithhold(List.of("reply"))));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.withhold");
  }

  @Test
  void refusesKeepingTheDeployWhileLeavingItsUndoToTheMaster() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(
                    MasterConfig.defaults().withMode("act").withMine(List.of("reply", "deploy"))));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.mine");
  }

  @Test
  void refusesKeepingTheRevertWhileLeavingItsDoToTheMaster() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(
                    MasterConfig.defaults().withMode("act").withMine(List.of("reply", "revert"))));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.mine");
  }

  @Test
  void findsNothingWrongWithShippingUnattendedWhileRepliesAndTheSharedBranchStayYours()
      throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(
                    MasterConfig.defaults()
                        .withMode("act")
                        .withMine(List.of("reply", "deploy", "revert"))));
    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesKeepingEveryStepWhereSayingJudgeMeansTheSame() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(
                    MasterConfig.defaults()
                        .withMode("act")
                        .withMine(
                            List.of(
                                "ship", "deploy", "revert", "reply", "answer", "plan", "repo",
                                "lift"))));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.mine");
  }

  @Test
  void refusesToRunReviewerWhoseBriefWasNeverCopiedFromTheShippedOne() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("judge")));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.brief");
  }

  @Test
  void judgesByTheShippedBriefWhereNoneWasCopied() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md.dist"), "the shipped standards");
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("judge")));
    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesBriefNamedByHandThatIsNotThere() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("judge").withBrief("mine.md")));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.brief");
  }

  @Test
  void refusesModelTheRuntimeRunningTheSessionsCannotBeTold() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "what I care about\n");
    when(agentRuntime.displayName()).thenReturn("Codex");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("judge").withModel("fable")));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.model");
  }

  @Test
  void acceptsMasterModelWhenTheConfigListsModelsForRuntimeListingNone()
      throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "what I care about\n");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(ConfigFile.AgentConfig.defaults().withModels(List.of("gpt-5")))
                .withMaster(MasterConfig.defaults().withMode("judge").withModel("gpt-5")));
    assertThat(check.problems()).isEmpty();
  }

  @Test
  void acceptsModeWhoseBriefTheInstallActuallyCarries() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "what I care about\n");
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));
    assertThat(check.problems()).isEmpty();
  }

  @Test
  void acceptsKeepingTheAttachingOfRepositoryForYourself() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(
                    MasterConfig.defaults().withMode("act").withMine(List.of("plan", "repo"))));
    assertThat(check.problems()).isEmpty();
  }
}
