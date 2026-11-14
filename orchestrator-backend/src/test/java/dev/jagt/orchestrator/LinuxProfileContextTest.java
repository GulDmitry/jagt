package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.linux.LibNotifyNotifier;
import dev.jagt.orchestrator.adapter.linux.LinuxKittyTerminalDriver;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.port.UserNotifier;
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
      "orchestrator.usage-limits.enabled=false",
      "orchestrator.platform=linux"
    })
class LinuxProfileContextTest {

  @TempDir static Path root;

  @MockitoBean private EditorDriver editorDriver;

  private final UserNotifier userNotifier;
  private final TerminalDriver terminalDriver;

  private final ApplicationContext context;

  @Autowired
  LinuxProfileContextTest(
      UserNotifier userNotifier, TerminalDriver terminalDriver, ApplicationContext context) {
    this.userNotifier = userNotifier;
    this.terminalDriver = terminalDriver;
    this.context = context;
  }

  @DynamicPropertySource
  static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry)
      throws IOException {
    ShippedRoot.configure(registry, root);
  }

  @Test
  void picksTheLinuxNotifierAndTerminalWithoutAnyMacOsBeanBeingRequired() {
    assertThat(userNotifier).isInstanceOf(LibNotifyNotifier.class);
    assertThat(terminalDriver).isInstanceOf(LinuxKittyTerminalDriver.class);
  }

  @Test
  void checksTheClaudeSandboxAndLeavesTheMacKeychainOut() {
    assertThat(context.containsBean("claudeSandboxCheck")).isTrue();
    assertThat(context.containsBean("claudeKeychain")).isFalse();
  }
}
