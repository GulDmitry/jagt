package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.macos.GhosttyTerminalDriver;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.port.UserNotifier;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
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
      "orchestrator.usage-limits.enabled=false",
      "orchestrator.terminal=ghostty"
    })
@ResourceLock("spring-logging")
class GhosttyProfileContextTest {

  @TempDir static Path root;

  @MockitoBean private EditorDriver editorDriver;

  @MockitoBean private UserNotifier userNotifier;

  private final TerminalDriver terminalDriver;

  @Autowired
  GhosttyProfileContextTest(TerminalDriver terminalDriver) {
    this.terminalDriver = terminalDriver;
  }

  @DynamicPropertySource
  static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry)
      throws IOException {
    ShippedRoot.configure(registry, root);
  }

  @Test
  void picksTheGhosttyDriverWhereTheTerminalKeyNamesIt() {
    assertThat(terminalDriver).isInstanceOf(GhosttyTerminalDriver.class);
  }
}
