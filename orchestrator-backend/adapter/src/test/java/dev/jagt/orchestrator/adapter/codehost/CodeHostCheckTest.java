package dev.jagt.orchestrator.adapter.codehost;

import static dev.jagt.orchestrator.adapter.codehost.TestShopProjectBuilder.shopProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.codehost.command.CommandPipeline;
import dev.jagt.orchestrator.adapter.codehost.notes.CommandNotes;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeHostCheckTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final CodeHostCheck check =
      new CodeHostCheck(
          configService,
          List.of(new CommandPipeline(mock(Processes.class), new ReachLedger()), new NoCodeHost()),
          List.of(new CommandNotes(mock(Processes.class), new ReachLedger())));

  @Test
  void refusesCodeHostNoDriverAnswersForNamingTheKey() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(Map.of("shop", shopProject().withKind("bitbucket").build())));

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
                    Map.of(
                        "shop",
                        shopProject()
                            .withKind("command")
                            .withPipelineCommand(List.of("no-such-ci-program", "{sha}"))
                            .build())));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.pipelineCommand");
  }

  @Test
  void asksNothingOfProjectThatChoosesNone() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(Map.of("shop", shopProject().withKind("none").build())));

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesCommandHostWhoseNotePostingProgramIsMissingNamingTheKey() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject()
                            .withKind("command")
                            .withPipelineCommand(List.of("sh", "{sha}"))
                            .withPostCommand(List.of("no-such-notes-program", "{request}"))
                            .build())));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.postCommand");
  }
}
