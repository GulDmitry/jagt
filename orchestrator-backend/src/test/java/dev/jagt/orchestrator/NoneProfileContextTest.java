package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.channel.NoNotifier;
import dev.jagt.orchestrator.adapter.terminal.NoEditorDriver;
import dev.jagt.orchestrator.adapter.terminal.NoTerminalDriver;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.host.KeepAwake;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    properties = {
      "spring.config.import=",
      "orchestrator.open-terminal-window=false",
      "orchestrator.startup-checks=false",
      "orchestrator.usage-limits.enabled=false",
      "orchestrator.platform=linux",
      "orchestrator.terminal=none",
      "orchestrator.notifier=none",
      "orchestrator.keep-awake=none",
      "orchestrator.editor=none"
    })
class NoneProfileContextTest {

  @TempDir static Path root;

  private final TerminalDriver terminalDriver;
  private final UserNotifier userNotifier;
  private final EditorDriver editorDriver;
  private final ApplicationContext context;

  @Autowired
  NoneProfileContextTest(
      TerminalDriver terminalDriver,
      UserNotifier userNotifier,
      EditorDriver editorDriver,
      ApplicationContext context) {
    this.terminalDriver = terminalDriver;
    this.userNotifier = userNotifier;
    this.editorDriver = editorDriver;
    this.context = context;
  }

  @DynamicPropertySource
  static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry)
      throws IOException {
    ShippedRoot.configure(registry, root);
  }

  @Test
  void startsWithNoTerminalNotifierKeepAwakeAndEditor() {
    assertThat(terminalDriver).isInstanceOf(NoTerminalDriver.class);
    assertThat(userNotifier).isInstanceOf(NoNotifier.class);
    assertThat(editorDriver).isInstanceOf(NoEditorDriver.class);
    assertThat(context.getBeansOfType(KeepAwake.class)).isEmpty();
  }
}
