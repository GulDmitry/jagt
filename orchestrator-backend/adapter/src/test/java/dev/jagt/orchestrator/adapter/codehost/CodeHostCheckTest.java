package dev.jagt.orchestrator.adapter.codehost;

import static dev.jagt.orchestrator.adapter.codehost.HostFixtures.project;
import static dev.jagt.orchestrator.adapter.codehost.HostFixtures.serving;
import static dev.jagt.orchestrator.adapter.codehost.HostFixtures.shopOn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ReachLedger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeHostCheckTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final CodeHostCheck check =
      new CodeHostCheck(
          configService,
          List.of(new CommandPipeline(mock(Processes.class), new ReachLedger()), new NoCodeHost()));

  @Test
  void refusesCodeHostNoDriverAnswersForNamingTheKey() {
    serving(configService, shopOn("bitbucket"));

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
    serving(configService, shopOn("none"));

    assertThat(check.problems()).isEmpty();
  }
}
