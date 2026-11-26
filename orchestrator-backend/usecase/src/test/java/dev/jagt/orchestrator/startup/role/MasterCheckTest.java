package dev.jagt.orchestrator.startup.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.service.ConfigFile.CodeReviewConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.KeptNumbers;
import dev.jagt.orchestrator.service.master.MasterBriefs;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import dev.jagt.orchestrator.testing.FakeHost;
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
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());
    check =
        new MasterCheck(
            configService, new MasterBriefs(paths, new SessionBrief(paths)), agentRuntime);
  }

  @Test
  void findsNothingWrongWithTheSettingEveryInstallShipsWith() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md.dist"), "the shipped standards");
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults()));
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
                .withConfigFile(root.resolve("jagt.yml").toString()),
            FakeHost.machine());
    ConfigService real =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            agentRuntime,
            mock(dev.jagt.orchestrator.service.master.ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn())),
            new KeptNumbers(new ReachLedger()));

    MasterCheck realCheck =
        new MasterCheck(real, new MasterBriefs(paths, new SessionBrief(paths)), agentRuntime);

    assertThat(realCheck.problems())
        .singleElement()
        .satisfies(problem -> {
          assertThat(problem.key()).isEqualTo("orchestrator.roles.master.mode");
          assertThat(problem.text()).startsWith("orchestrator.roles.master.mode: 'supervise'");
        });
  }

  @Test
  void refusesModeThisMachineDoesNotHave() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("supervise")));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.mode");
  }

  @Test
  void refusesMasterThatShipsWhileEveryDraftedReplyStillWaitsForYouToPostIt() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withArbiter(ArbiterConfig.defaults().withMode("act"))
                .withCodeReview(new CodeReviewConfig(null, false, null, null, null)));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.mode");
  }

  @Test
  void refusesTheRetiredNameRatherThanSilentlyGivingTheMasterEveryStep() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withArbiter(
                    ArbiterConfig.defaults().withMode("act").withWithhold(List.of("reply"))));
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
                .withArbiter(
                    ArbiterConfig.defaults().withMode("act").withMine(List.of("reply", "deploy"))));
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
                .withArbiter(
                    ArbiterConfig.defaults().withMode("act").withMine(List.of("reply", "revert"))));
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
                .withArbiter(
                    ArbiterConfig.defaults()
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
                .withArbiter(
                    ArbiterConfig.defaults()
                        .withMode("act")
                        .withMine(
                            List.of(
                                "ship", "deploy", "revert", "reply", "answer", "plan", "replan",
                                "repo", "lift"))));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.mine");
  }

  @Test
  void refusesToRunReviewerWhoseBriefWasNeverCopiedFromTheShippedOne() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("judge")));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.brief");
  }

  @Test
  void judgesByTheShippedBriefWhereNoneWasCopied() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md.dist"), "the shipped standards");
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("judge")));
    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesBriefNamedByHandThatIsNotThere() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withArbiter(ArbiterConfig.defaults().withMode("judge").withBrief("mine.md")));
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
                .withArbiter(ArbiterConfig.defaults().withMode("judge").withModel("fable")));
    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.master.model");
  }

  @Test
  void acceptsMasterModelWhenTheConfigListsModelsForRuntimeListingNone() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "what I care about\n");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(ConfigFile.AgentConfig.defaults().withModels(List.of("gpt-5")))
                .withArbiter(ArbiterConfig.defaults().withMode("judge").withModel("gpt-5")));
    assertThat(check.problems()).isEmpty();
  }

  @Test
  void acceptsModeWhoseBriefTheInstallActuallyCarries() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "what I care about\n");
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withArbiter(ArbiterConfig.defaults().withMode("act")));
    assertThat(check.problems()).isEmpty();
  }

  @Test
  void acceptsKeepingTheAttachingOfRepositoryForYourself() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "judge like me");
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withArbiter(
                    ArbiterConfig.defaults().withMode("act").withMine(List.of("plan", "repo"))));
    assertThat(check.problems()).isEmpty();
  }
}
