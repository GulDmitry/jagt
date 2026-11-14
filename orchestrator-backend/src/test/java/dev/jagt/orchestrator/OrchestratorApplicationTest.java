package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.service.TicketReader;
import dev.jagt.orchestrator.surface.mcp.McpController;
import dev.jagt.orchestrator.surface.ui.LogFileRoll;
import dev.jagt.orchestrator.surface.ui.PortGuard;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
class OrchestratorApplicationTest {

  @MockitoBean private UserNotifier userNotifier;

  @TempDir static Path root;

  @MockitoBean private EditorDriver editorDriver;

  private final ApplicationContext context;

  @Autowired
  OrchestratorApplicationTest(ApplicationContext context) {
    this.context = context;
  }

  @DynamicPropertySource
  static void keepConfigAndStateOutOfTheDevelopersOwnFilesEvenWhereTheyExportedThemOverTheRoot(
      DynamicPropertyRegistry registry) throws IOException {
    ShippedRoot.configure(registry, root);
  }

  @Test
  void theApplicationStartsWithEveryBeanWiredSoStartupBreakFailsHereAndNotOnLaunch() {
    assertThat(context.getBean(McpController.class)).isNotNull();
    assertThat(context.getBean(GlobalCommands.class)).isNotNull();
    assertThat(context.getBean(TicketReader.class)).isNotNull();
  }

  @Test
  void readsTheMacKeychainAndSkipsTheLinuxSandboxCheckOnTheDefaultPlatform() {
    assertThat(context.containsBean("claudeKeychain")).isTrue();
    assertThat(context.containsBean("claudeSandboxCheck")).isFalse();
  }

  @Test
  void keepsTheDevelopersOwnStateFileOutOfBootedContext() {
    assertThat(context.getBean(OrchestratorPaths.class).stateFile().toString())
        .startsWith(root.toString());
  }

  @Test
  void launchesWithTheLogFileRollListenerRegistered() {
    assertThat(OrchestratorApplication.application().getListeners())
        .hasAtLeastOneElementOfType(LogFileRoll.class);
  }

  @Test
  void launchesWithThePortGuardListenerRegistered() {
    assertThat(OrchestratorApplication.application().getListeners())
        .hasAtLeastOneElementOfType(PortGuard.class);
  }
}
