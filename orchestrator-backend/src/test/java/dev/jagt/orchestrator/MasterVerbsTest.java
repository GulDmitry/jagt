package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.command.master.MasterPresses;
import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MasterVerbsTest {

  private final CommandService commands = mock(CommandService.class);

  private final MasterPresses presses =
      new MasterPresses(commands, mock(GlobalCommands.class), mock(ConfigService.class));

  @ParameterizedTest
  @EnumSource(TaskAction.class)
  void theMasterPressesEveryVerbTheBoardOffers(TaskAction action) {
    String task = "ABC-" + action.id();

    presses.press(task, action.id(), null, null);

    verify(commands).execute(task, action, null);
  }

  @ParameterizedTest
  @EnumSource(TaskAction.class)
  void theMasterHasMoveHeldToCaseForEveryVerbTheBoardOffers(TaskAction action) throws IOException {
    String shipped = Files.readString(Path.of("../roles/master/cases.md.dist"));

    assertThat(MasterMove.pressed()).contains(action.id());
    assertThat(shipped).contains("move: " + action.id() + "\n");
  }
}
