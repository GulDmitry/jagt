package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.command.MasterPresses;
import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import java.io.IOException;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.port.UserNotifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "spring.config.import=",
      "orchestrator.open-terminal-window=false",
      "orchestrator.startup-checks=false",
      "orchestrator.usage-limits.enabled=false"
    })
@ResourceLock("spring-logging")
class MasterVerbsTest {

  @MockitoBean private UserNotifier userNotifier;

  @MockitoBean private EditorDriver editorDriver;

  @TempDir static Path root;

  @MockitoBean private CommandService commands;

  @Autowired private MasterPresses presses;

  @Autowired private GlobalCommands globals;

  @DynamicPropertySource
  static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry)
      throws IOException {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    Files.copy(
        Path.of("../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/qa"));
    Files.copy(Path.of("../roles/qa/role.yml.dist"), root.resolve("roles/qa/role.yml.dist"));
    registry.add("orchestrator.root", () -> root.toString());
    registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
    registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
  }

  @ParameterizedTest
  @EnumSource(TaskAction.class)
  void theMasterPressesEveryVerbTheBoardOffers(TaskAction action) {
    presses.press("ABC-42", action.id(), null);

    verify(commands).execute("ABC-42", action, null);
  }

  @ParameterizedTest
  @EnumSource(TaskAction.class)
  void theMasterHasAMoveHeldToACaseForEveryVerbTheBoardOffers(TaskAction action)
      throws IOException {
    String shipped = Files.readString(Path.of("../roles/master/cases.md.dist"));

    assertThat(MasterMove.pressed()).contains(action.id());
    assertThat(shipped).contains("move: " + action.id() + "\n");
  }

  @Test
  void everyCommandIsRunnableByTheMasterOrSaysWhyItIsNot() {
    assertThat(globals.all())
        .allSatisfy(
            command ->
                assertThat(command.whyNotTheMaster().map(String::isBlank).orElse(false))
                    .as(command.id())
                    .isFalse());
  }
}
