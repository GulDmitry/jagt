package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class MasterMineCheckTest {

  private final ConfigService configService = mock(ConfigService.class);

  private record Spend() implements GlobalCommand {
    @Override
    public String id() {
      return "spend";
    }

    @Override
    public String hint() {
      return "what the calls cost";
    }

    @Override
    public int rank() {
      return 15;
    }

    @Override
    public String run(String tail) {
      return "spent";
    }
  }

  @Test
  void refusesAWordKeptForYourselfThatNamesNoStepVerbOrCommand() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(
                    new MasterConfig("judge", null, null, List.of("ship", "teleport"), null)));

    assertThat(new MasterMineCheck(configService, new GlobalCommands(List.of())).problems())
        .singleElement()
        .satisfies(
            problem -> {
              assertThat(problem.key()).isEqualTo("orchestrator.roles.master.mine");
              assertThat(problem.text()).contains("'teleport'").doesNotContain("'ship'");
            });
  }

  @Test
  void acceptsAStepAVerbOfATaskAndACommandKeptForYourself() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(
                    new MasterConfig(
                        "act", null, null, List.of("plan", "done", "remove-repo", "spend"), null)));

    assertThat(
            new MasterMineCheck(configService, new GlobalCommands(List.of(new Spend()))).problems())
        .isEmpty();
  }

  @Test
  void checksNothingWhereTheMasterIsOff() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withMaster(new MasterConfig("off", null, null, List.of("teleport"), null)));

    assertThat(new MasterMineCheck(configService, new GlobalCommands(List.of())).problems())
        .isEmpty();
  }
}
