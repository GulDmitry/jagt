package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.service.stop.BoardStop;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
                .withData(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString())
                .withStateFile(root.resolve("state.json").toString()),
            FakeHost.machine());
    stops = new ActStops(new MasterBrake(paths), new BoardStop(paths, new FakeClock()));
    service =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            mock(ActStops.class),
            checkers,
            new KeptNumbers(new ReachLedger()));
  }

  @Test
  void acceptanceIsOffWhereTheRolesKeyDoesNotSayOtherwise() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n");

    boolean enabled = service.load().role("acceptance").enabledOrDefault();

    assertThat(enabled).isFalse();
  }

  @Test
  void rolesKeyNamingNoRoleWithJobsNeverSwitchesTheSessionsDeployCheckOff() throws Exception {
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

    boolean any = service.anyCheckerEnabled();

    assertThat(any).isFalse();
  }

  @Test
  void deployOnApprovalIsOffUntilJagtYmlTurnsItOn() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n");
    boolean unset = service.load().deploy().onApprovalOrDefault();
    Files.writeString(configFile, "orchestrator:\n  deploy:\n    onApproval: true\n");

    boolean written = service.load().deploy().onApprovalOrDefault();

    assertThat(unset).isFalse();
    assertThat(written).isTrue();
  }

  @Test
  void jagtYmlWithTheOldMasterKeyStillActs() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n    mine: [ship]\n");

    ArbiterConfig master = service.load().arbiter();

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

    ArbiterConfig master = service.load().arbiter();

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

    ArbiterConfig master = service.load().arbiter();

    assertThat(master.mineOrDefault()).containsExactly("plan", "deploy", "revert");
    assertThat(master.modeOrDefault()).isEqualTo(MasterMode.ACT);
  }

  @Test
  void partialRolesMasterKeepsTheOldMode() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        "orchestrator:\n  master:\n    mode: judge\n  roles:\n    master:\n      mine: [reply]\n");

    ArbiterConfig master = service.load().arbiter();

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

    String mode = service.load().arbiter().mode();

    assertThat(mode).isEqualTo("off");
  }

  @Test
  void readsActAsJudgeWhileActIsStopped() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");

    String mode =
        new ConfigService(
                new ConfigSource(paths, new FakeConfigInputs()),
                mock(AgentRuntime.class),
                stops,
                checkers,
                new KeptNumbers(new ReachLedger()))
            .load()
            .arbiter()
            .mode();

    assertThat(mode).isEqualTo("judge");
  }

  @Test
  void readsActAsJudgeWhileTheBoardIsStoppedAndKeepsTheConfiguredModeInTheFile() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");
    Files.writeString(root.resolve("board-stopped"), "1000");
    ConfigService stoppedBoard =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            stops,
            checkers,
            new KeptNumbers(new ReachLedger()));

    String mode = stoppedBoard.load().arbiter().mode();

    assertThat(mode).isEqualTo("judge");
    assertThat(Files.readString(configFile)).contains("mode: act");
  }

  @Test
  void readsAnEditToTheConfigFileOnTheNextLoad() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  master:\n    mode: off\n");
    ConfigService configService = service;
    configService.load();
    Files.writeString(configFile, "orchestrator:\n  master:\n    mode: act\n");

    String mode = configService.load().arbiter().mode();

    assertThat(mode).isEqualTo("act");
  }

  @ParameterizedTest
  @CsvSource({"true, act", "false, off"})
  void leavesTheMasterActingByDefaultOnlyWhereTheRuntimeReadsWhatTheHumanTyped(
      boolean reads, String expected) throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n");
    AgentRuntime runtime = mock(AgentRuntime.class);
    when(runtime.readsWhatTheHumanTyped()).thenReturn(reads);

    String mode =
        new ConfigService(
                new ConfigSource(paths, new FakeConfigInputs()),
                runtime,
                mock(ActStops.class),
                checkers,
                new KeptNumbers(new ReachLedger()))
            .load()
            .arbiter()
            .mode();

    assertThat(mode).isEqualTo(expected);
  }

  @Test
  void leavesTheMasterOffForBlankModeWhereTheRuntimeCannotReadTheHuman() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n  master:\n    mode: \"\"\n");

    String mode = service.load().arbiter().mode();

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

  @Test
  void readsAnEmptySectionAsAnEmptyConfigurationWithItsMasterDefault() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "other: 1\n");
    AgentRuntime runtime = mock(AgentRuntime.class);
    when(runtime.readsWhatTheHumanTyped()).thenReturn(true);

    ConfigFile config =
        new ConfigService(
                new ConfigSource(paths, new FakeConfigInputs()),
                runtime,
                stops,
                checkers,
                new KeptNumbers(new ReachLedger()))
            .load();

    assertThat(config.projects()).isEmpty();
    assertThat(config.arbiter().mode()).isEqualTo("act");
  }

  @Test
  void answersWhichProjectIsConfiguredAndRefusesAnUnknownOne() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        "orchestrator:\n  projects:\n    api: {path: /srv/api, baseBranch: origin/main}\n");
    ConfigService config =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            stops,
            checkers,
            new KeptNumbers(new ReachLedger()));

    assertThat(config.project("api").path()).isEqualTo("/srv/api");
    assertThatThrownBy(() -> config.project("web"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown project 'web'. Known projects: [api]");
  }

  @Test
  void placesProjectNamedByUrlWithNoPathInItsFolderUnderTheDataFolder() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  projects:\n    api: {url: 'https://example.invalid/api.git', baseBranch: origin/main}\n");

    String path = service.project("api").path();

    assertThat(path).isEqualTo(root.resolve("projects/api").toString());
  }

  @Test
  void keepsTheWrittenPathOfProjectNamedByUrlWhereThatFolderExists() throws Exception {
    Path existing = Files.createDirectories(root.resolve("mine"));
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  projects:\n    api: {url: 'https://example.invalid/api.git', path: '"
            + existing
            + "', baseBranch: origin/main}\n");

    String path = service.project("api").path();

    assertThat(path).isEqualTo(existing.toString());
  }

  @Test
  void placesProjectNamedByUrlWhoseWrittenPathIsMissingInItsFolderUnderTheDataFolder()
      throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  projects:\n    api: {url: 'https://example.invalid/api.git', path: /gone/api, baseBranch: origin/main}\n");

    String path = service.project("api").path();

    assertThat(path).isEqualTo(root.resolve("projects/api").toString());
  }

  @Test
  void keepsTheLastValidRoundsAndMarksTheBoardWhenLiveEditWritesOneOutsideItsRange()
      throws Exception {
    ReachLedger reach = new ReachLedger();
    ConfigService live =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            stops,
            checkers,
            new KeptNumbers(reach));
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  loopBreaker: { rounds: 7 }\n");
    live.load();

    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  loopBreaker: { rounds: -1 }\n");

    assertThat(live.load().loopBreaker().roundsOrDefault()).isEqualTo(7);
    assertThat(reach.standing())
        .containsExactly(
            new ReachLost(
                "config",
                "orchestrator.loopBreaker.rounds",
                List.of(),
                "is -1: it must be 1 to 19; the read keeps 7"));
  }

  @Test
  void readsTheDefaultAndMarksTheBoardWhenNoValidValueWasReadBeforeTheEdit() throws Exception {
    ReachLedger reach = new ReachLedger();
    ConfigService live =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            stops,
            checkers,
            new KeptNumbers(reach));
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  maxTasks: 0\n");

    assertThat(live.load().maxTasksOrDefault()).isEqualTo(50);
    assertThat(reach.standing())
        .extracting(ReachLost::said)
        .containsExactly("is 0: it must be at least 1; the read keeps its default");
  }

  @Test
  void remembersValidNumberReadWhileAnotherKeyStandsRefused() throws Exception {
    ConfigService live =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            stops,
            checkers,
            new KeptNumbers(new ReachLedger()));
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  maxTasks: 0\n  loopBreaker: { rounds: 7 }\n");
    live.load();

    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  maxTasks: 0\n  loopBreaker: { rounds: -1 }\n");

    assertThat(live.load().loopBreaker().roundsOrDefault()).isEqualTo(7);
  }

  @Test
  void clearsTheBoardMarkOnTheReadThatFindsTheEditCorrected() throws Exception {
    ReachLedger reach = new ReachLedger();
    ConfigService live =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            stops,
            checkers,
            new KeptNumbers(reach));
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  loopBreaker: { rounds: 99 }\n");
    live.load();

    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  loopBreaker: { rounds: 5 }\n");

    assertThat(live.load().loopBreaker().roundsOrDefault()).isEqualTo(5);
    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void refusesLiveMaxIntervalBelowTheMinIntervalInsteadOfRaisingIt() throws Exception {
    ReachLedger reach = new ReachLedger();
    ConfigService live =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            stops,
            checkers,
            new KeptNumbers(reach));
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  autoReview: { minIntervalMinutes: 10, maxIntervalMinutes: 9 }\n");

    assertThat(live.load().autoReview().maxIntervalMinutesOrDefault()).isEqualTo(60);
    assertThat(reach.standing())
        .extracting(ReachLost::said)
        .containsExactly("is 9: it must be at least 10; the read keeps its default");
  }

  @Test
  void handsTheLoopBreakerTheOwnerConfiguredAtTheMomentItIsAsked() throws Exception {
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

    var breaker = service.current();

    assertThat(breaker.rounds()).isEqualTo(5);
    assertThat(breaker.ships()).isEqualTo(6);
    assertThat(breaker.stalledRounds()).isEqualTo(7);
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

    List<String> unknown = service.load().unknownRoles(List.of("acceptance"));

    assertThat(unknown).containsExactly("foo");
  }

  @Test
  void theMasterWrittenTwiceIsNoUnknownRole() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile,
        "orchestrator:\n  master:\n    mode: judge\n  roles:\n    master:\n      mine: [reply]\n");

    List<String> unknown = service.load().unknownRoles(List.of("acceptance"));

    assertThat(unknown).isEmpty();
  }

  @Test
  void anAcceptanceWrittenUnderRolesIsUnknownWhereNoAcceptanceRoleIsInstalled() throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(configFile, "orchestrator:\n  roles:\n    acceptance:\n      model: haiku\n");

    List<String> unknown = service.load().unknownRoles(List.of());

    assertThat(unknown).containsExactly("acceptance");
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

    ConfigFile config = service.load();

    Object expected = new Yaml().<java.util.Map<String, Object>>load(written).get("orchestrator");
    JsonNode actual = mapper.valueToTree(config);
    assertThat(actual).isEqualTo(mapper.<JsonNode>valueToTree(expected));
  }
}
