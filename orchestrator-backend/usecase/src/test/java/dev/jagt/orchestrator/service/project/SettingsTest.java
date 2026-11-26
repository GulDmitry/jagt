package dev.jagt.orchestrator.service.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.KeptNumbers;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.master.MasterSettings;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.service.role.InstalledRoles;
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

class SettingsTest {

  @TempDir Path root;

  private Path configFile;
  private Settings settings;
  private OrchestratorPaths paths;
  private ConfigSource source;
  private ConfigWriter writer;

  @BeforeEach
  void wire() throws Exception {
    configFile =
        Files.writeString(
            root.resolve("jagt.yml"),
            """
            orchestrator:
              projects:
                billing: {path: /srv/billing, baseBranch: origin/main}
              # who answers
              autoReview:
                enabled: true   # polling
              notifications:
                channels:
                  desktop: { kind: desktop }
            """);
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md.dist"), "brief");
    paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(configFile.toString()),
            FakeHost.machine());
    source = new ConfigSource(paths, new FakeConfigInputs());
    ConfigService config =
        new ConfigService(
            source,
            mock(AgentRuntime.class),
            mock(ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn())),
            new KeptNumbers(new ReachLedger()));
    writer = new ConfigWriter(config, paths);
    settings = new Settings(source, writer, List.of(new MasterSettings(new InstalledRoles(paths))));
  }

  @Test
  void readsWhatTheFileHoldsAndNothingWhereTheDefaultStands() {
    List<Settings.View> views = settings.read();

    assertThat(views)
        .filteredOn(view -> view.setting().key().equals("autoReview.enabled"))
        .singleElement()
        .satisfies(view -> assertThat(view.value()).isEqualTo(true));
    assertThat(views)
        .filteredOn(view -> view.setting().key().equals("autoReview.windowHours"))
        .singleElement()
        .satisfies(view -> assertThat(view.value()).isNull());
    assertThat(views)
        .filteredOn(view -> view.setting().key().equals("projects"))
        .singleElement()
        .satisfies(view -> assertThat(view.value()).isEqualTo(List.of("billing")));
    assertThat(views)
        .filteredOn(view -> view.setting().key().equals("notifications.channels"))
        .singleElement()
        .satisfies(view -> assertThat(view.value()).isEqualTo(List.of("desktop")));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "codeReview.postReviewReplies | true",
        "codeReview.mrTitlePattern | {ticket} {title}",
        "autoReview.windowHours | 24",
        "infraWait.backoff | 5m, 15m, 45m",
        "tracker.everyMinutes | 10",
        "loopBreaker.stalledRounds | 3",
        "conflictStallMinutes | 20"
      })
  void namesTheDefaultThatAppliesWhileTheFileLeavesTheKeyOut(String key, String fallback) {
    assertThat(settings.read())
        .filteredOn(view -> view.setting().key().equals(key))
        .singleElement()
        .satisfies(view -> assertThat(view.setting().fallback()).isEqualTo(fallback));
  }

  @Test
  void showsNoRoleExceptTheInstalledMastersModeAndNoModelOrAgentSetting() {
    assertThat(settings.read())
        .extracting(view -> view.setting().key())
        .noneMatch(key -> key.contains("model") || key.startsWith("agent"))
        .filteredOn(key -> key.startsWith("roles."))
        .containsExactly("roles.master.mode");
  }

  @Test
  void showsNoMasterKeyAndRefusesToChangeOneWhenTheMasterIsNotInstalled() throws Exception {
    Files.delete(root.resolve("roles/master/brief.md.dist"));
    Settings without =
        new Settings(source, writer, List.of(new MasterSettings(new InstalledRoles(paths))));

    assertThat(without.read())
        .extracting(view -> view.setting().section())
        .doesNotContain("master");
    assertThatThrownBy(() -> without.change("roles.master.mode", "act"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> without.change("loopBreaker.rounds", 3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changesKeyWhereItStandsAndKeepsItsComment() throws Exception {
    Settings.Change change = settings.change("autoReview.enabled", false);

    assertThat(change.restart()).isFalse();
    assertThat(Files.readString(configFile)).contains("    enabled: false  # polling");
  }

  @Test
  void saysRestartForKeyJagtReadsOnlyAtStart() throws Exception {
    Settings.Change change = settings.change("infraWait.backoff", List.of("1m", "2m"));

    assertThat(change.restart()).isTrue();
    assertThat(Files.readString(configFile)).contains("backoff: [\"1m\",\"2m\"]");
  }

  @ParameterizedTest
  @CsvSource({"autoReview.enabled,yes", "autoReview.windowHours,many", "tracker.mode,sometimes"})
  void refusesValueOfTheWrongTypeOrOutsideTheChoices(String key, String value) throws Exception {
    String before = Files.readString(configFile);

    assertThatThrownBy(() -> settings.change(key, value))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.readString(configFile)).isEqualTo(before);
  }

  @Test
  void refusesKeyItDoesNotOfferAndOneThatOnlyListsNames() {
    assertThatThrownBy(() -> settings.change("agent.cli", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> settings.change("projects", List.of("x")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
