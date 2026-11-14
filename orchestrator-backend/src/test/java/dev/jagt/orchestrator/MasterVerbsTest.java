package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.command.MasterPresses;
import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.service.CommandService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "spring.config.import=",
      "orchestrator.open-terminal-window=false",
      "orchestrator.startup-checks=false",
      "orchestrator.usage-limits.enabled=false"
    })
class MasterVerbsTest {

  @MockitoBean private UserNotifier userNotifier;

  @MockitoBean private EditorDriver editorDriver;

  @TempDir static Path root;

  @MockitoBean(reset = MockReset.NONE)
  private CommandService commands;

  private final MasterPresses presses;

  private final GlobalCommands globals;

  @Autowired
  MasterVerbsTest(MasterPresses presses, GlobalCommands globals) {
    this.presses = presses;
    this.globals = globals;
  }

  @DynamicPropertySource
  static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry)
      throws IOException {
    ShippedRoot.configure(registry, root);
  }

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

  @Test
  void everyCommandIsRunnableByTheMasterOrSaysWhyItIsNot() {
    assertThat(globals.all())
        .allSatisfy(command ->
            assertThat(command.whyNotTheMaster().map(String::isBlank).orElse(false))
                .as(command.id())
                .isFalse());
  }
}
