package dev.jagt.orchestrator.adapter;

import static dev.jagt.orchestrator.adapter.codehost.TestShopProjectBuilder.shopProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.flows.TestAcceptanceRoleSettingsBuilder;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.service.role.RoleKits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectProgramsReachTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final ReachLedger reach = new ReachLedger();

  @Test
  void headNamesProjectAndProgramWhereVerifyCommandNamesOneTheImageLacks() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject()
                            .withVerifyCommand(List.of("no-such-build-tool", "test"))
                            .build())));

    new ProjectProgramsReach(configService, new RoleKits(List.of()), reach).run();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(lost -> {
          assertThat(lost.driver()).isEqualTo("verifyCommand");
          assertThat(lost.said())
              .contains("shop", "no-such-build-tool", "Extend the image (docs/server.md)");
        });
  }

  @Test
  void headNamesTheProgramWhereSetupCommandNamesOneTheImageLacks() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject().withSetupCommand(List.of("no-such-installer")).build())));

    new ProjectProgramsReach(configService, new RoleKits(List.of()), reach).run();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(lost -> assertThat(lost.driver()).isEqualTo("setupCommand"));
  }

  @Test
  void headClearsOnTheFirstReadThatFindsTheProgram() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject()
                            .withVerifyCommand(List.of("no-such-build-tool", "test"))
                            .build())),
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject().withVerifyCommand(List.of("git", "status")).build())));
    ProjectProgramsReach programs =
        new ProjectProgramsReach(configService, new RoleKits(List.of()), reach);
    programs.run();

    programs.run();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void headSaysNothingWhereProgramIsInstalledPathedOrAbsent() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject().withVerifyCommand(List.of("git", "status")).build(),
                        "wrapped",
                        shopProject().withVerifyCommand(List.of("./no-such-wrapper")).build(),
                        "bare",
                        shopProject().build())));

    new ProjectProgramsReach(configService, new RoleKits(List.of()), reach).run();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void headNamesRoleAndProgramWhereAcceptanceFlowsNeedProgramTheImageLacks(@TempDir Path root)
      throws Exception {
    var kits =
        TestAcceptanceRoleSettingsBuilder.acceptanceRoleSettings()
            .withRoot(root)
            .build(mock(AgentRuntime.class), mock(ActStops.class));
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        "flows: {driver: playwright, path: /f, command: [no-such-npx, playwright, test]}\n");
    when(configService.load()).thenReturn(ConfigFile.defaults());

    new ProjectProgramsReach(configService, kits, reach).run();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(lost -> assertThat(lost.said()).contains("acceptance", "no-such-npx"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "flows: {driver: none, command: [no-such-npx]}",
        "flows: {driver: playwright, path: /f, command: [git, status]}"
      })
  void headSaysNothingWhereAcceptanceFlowsAreOffOrTheirProgramIsInstalled(
      String written, @TempDir Path root) throws Exception {
    var kits =
        TestAcceptanceRoleSettingsBuilder.acceptanceRoleSettings()
            .withRoot(root)
            .build(mock(AgentRuntime.class), mock(ActStops.class));
    Files.writeString(root.resolve("roles/acceptance/role.yml"), written + "\n");
    when(configService.load()).thenReturn(ConfigFile.defaults());

    new ProjectProgramsReach(configService, kits, reach).run();

    assertThat(reach.standing()).isEmpty();
  }
}
