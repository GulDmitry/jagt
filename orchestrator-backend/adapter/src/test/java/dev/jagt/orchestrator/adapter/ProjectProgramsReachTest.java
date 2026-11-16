package dev.jagt.orchestrator.adapter;

import static dev.jagt.orchestrator.adapter.codehost.TestShopProjectBuilder.shopProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectProgramsReachTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final ReachLedger reach = new ReachLedger();

  @Test
  void headNamesProjectAndProgramWhereVerifyCommandNamesOneTheImageLacks() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject()
                            .withVerifyCommand(List.of("no-such-build-tool", "test"))
                            .build())));

    new ProjectProgramsReach(configService, reach).run();

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
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject().withSetupCommand(List.of("no-such-installer")).build())));

    new ProjectProgramsReach(configService, reach).run();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(lost -> assertThat(lost.driver()).isEqualTo("setupCommand"));
  }

  @Test
  void headClearsOnTheFirstReadThatFindsTheProgram() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject()
                            .withVerifyCommand(List.of("no-such-build-tool", "test"))
                            .build())),
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject().withVerifyCommand(List.of("git", "status")).build())));
    ProjectProgramsReach programs = new ProjectProgramsReach(configService, reach);
    programs.run();

    programs.run();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void headSaysNothingWhereProgramIsInstalledPathedOrAbsent() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject().withVerifyCommand(List.of("git", "status")).build(),
                        "wrapped",
                        shopProject().withVerifyCommand(List.of("./no-such-wrapper")).build(),
                        "bare",
                        shopProject().build())));

    new ProjectProgramsReach(configService, reach).run();

    assertThat(reach.standing()).isEmpty();
  }
}
