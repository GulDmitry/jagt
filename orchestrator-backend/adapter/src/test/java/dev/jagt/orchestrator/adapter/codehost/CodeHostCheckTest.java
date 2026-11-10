package dev.jagt.orchestrator.adapter.codehost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CodeHostCheckTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final CodeHostCheck check =
      new CodeHostCheck(
          configService,
          List.of(new CommandPipeline(mock(Processes.class), new ReachLedger()), new NoCodeHost()));

  private static ProjectConfig project(@Nullable String codeHost, List<String> pipelineCommand) {
    return new ProjectConfig(
        new ProjectConfig.Repository("/repo/shop", List.of(), null, List.of()),
        new ProjectConfig.Branches("origin/main", "dev", null),
        new ProjectConfig.CodeHost(
            codeHost, null, pipelineCommand, List.of(), List.of(), List.of(), List.of()),
        new ProjectConfig.Verify(List.of(), null));
  }

  @Test
  void refusesCodeHostNoDriverAnswersForNamingTheKey() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults().withProjects(Map.of("shop", project("bitbucket", List.of()))));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.codeHost");
  }

  @Test
  void refusesCommandHostWhoseProgramIsMissingNamingTheKey() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of("shop", project("command", List.of("no-such-ci-program", "{sha}")))));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.pipelineCommand");
  }

  @Test
  void asksNothingOfProjectThatChoosesNone() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("none", List.of()))));

    assertThat(check.problems()).isEmpty();
  }
}
