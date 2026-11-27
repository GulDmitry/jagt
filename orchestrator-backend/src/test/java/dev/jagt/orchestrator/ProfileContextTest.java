package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.channel.NoNotifier;
import dev.jagt.orchestrator.adapter.herdr.HerdrSessionHost;
import dev.jagt.orchestrator.adapter.herdr.HerdrWindowStream;
import dev.jagt.orchestrator.adapter.linux.LibNotifyNotifier;
import dev.jagt.orchestrator.adapter.linux.LinuxKittyTerminalDriver;
import dev.jagt.orchestrator.adapter.macos.GhosttyTerminalDriver;
import dev.jagt.orchestrator.adapter.terminal.AbstractKittyTerminalDriver;
import dev.jagt.orchestrator.adapter.terminal.NoEditorDriver;
import dev.jagt.orchestrator.adapter.terminal.NoTerminalDriver;
import dev.jagt.orchestrator.adapter.tmux.TmuxSessionHost;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.host.KeepAwake;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import dev.jagt.orchestrator.port.terminal.WindowStream;
import dev.jagt.orchestrator.service.ConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class ProfileContextTest {

  private final WebApplicationContextRunner shipped =
      new WebApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(OrchestratorApplication.class)
          .withPropertyValues(
              "spring.config.import=",
              "orchestrator.startup-checks=false",
              "orchestrator.usage-limits.enabled=false",
              "orchestrator.editor=none");

  @Test
  void picksTheGhosttyDriverWhereTheTerminalKeyNamesIt(@TempDir Path root) throws Exception {
    ShippedRoot.copyInto(root);

    shipped
        .withPropertyValues(
            "orchestrator.root=" + root,
            "orchestrator.config-file=" + root.resolve("jagt.yml"),
            "orchestrator.state-file=" + root.resolve("state.json"),
            "orchestrator.notifier=none",
            "orchestrator.terminal=ghostty")
        .run(context ->
            assertThat(context.getBean(TerminalDriver.class))
                .isInstanceOf(GhosttyTerminalDriver.class));
  }

  @Test
  void picksTheHerdrSessionHostWhereTheMultiplexerKeyNamesIt(@TempDir Path root) throws Exception {
    ShippedRoot.copyInto(root);

    shipped
        .withPropertyValues(
            "orchestrator.root=" + root,
            "orchestrator.config-file=" + root.resolve("jagt.yml"),
            "orchestrator.state-file=" + root.resolve("state.json"),
            "orchestrator.notifier=none",
            "orchestrator.terminal=none",
            "orchestrator.multiplexer=herdr")
        .run(context -> {
          assertThat(context.getBean(SessionHost.class)).isInstanceOf(HerdrSessionHost.class);
          assertThat(context.getBean(WindowStream.class)).isInstanceOf(HerdrWindowStream.class);
          assertThat(context.getBeansOfType(TmuxSessionHost.class)).isEmpty();
        });
  }

  @Test
  void runsEverySessionUnderTheViewerSessionNameInHerdr(@TempDir Path root) throws Exception {
    ShippedRoot.copyInto(root);
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  projects: {}\n  multiplexer: herdr\n  viewer:\n    session: agents\n");

    shipped
        .withPropertyValues(
            "orchestrator.root=" + root,
            "orchestrator.config-file=" + root.resolve("jagt.yml"),
            "orchestrator.state-file=" + root.resolve("state.json"),
            "orchestrator.notifier=none",
            "orchestrator.terminal=none",
            "orchestrator.multiplexer=herdr")
        .run(context -> {
          SessionHost host = context.getBean(SessionHost.class);
          String configured = context.getBean(ConfigService.class).load().viewer().session();
          assertThat(host).isInstanceOf(HerdrSessionHost.class);
          assertThat(host.sessionName(configured)).isEqualTo("agents");
        });
  }

  @Test
  void picksTmuxAsTheSessionHostWhereTheKeyIsUnset(@TempDir Path root) throws Exception {
    ShippedRoot.copyInto(root);

    shipped
        .withPropertyValues(
            "orchestrator.root=" + root,
            "orchestrator.config-file=" + root.resolve("jagt.yml"),
            "orchestrator.state-file=" + root.resolve("state.json"),
            "orchestrator.notifier=none",
            "orchestrator.terminal=none")
        .run(context -> {
          assertThat(context.getBean(SessionHost.class)).isInstanceOf(TmuxSessionHost.class);
          assertThat(context.getBeansOfType(HerdrSessionHost.class)).isEmpty();
        });
  }

  @Test
  void picksTheLinuxNotifierAndTerminalAndChecksTheSandboxWithoutAnyMacOsBeanBeingRequired(
      @TempDir Path root) throws Exception {
    ShippedRoot.copyInto(root);

    shipped
        .withPropertyValues(
            "orchestrator.root=" + root,
            "orchestrator.config-file=" + root.resolve("jagt.yml"),
            "orchestrator.state-file=" + root.resolve("state.json"),
            "orchestrator.platform=linux")
        .run(context -> {
          assertThat(context.getBean(UserNotifier.class)).isInstanceOf(LibNotifyNotifier.class);
          assertThat(context.getBean(TerminalDriver.class))
              .isInstanceOf(LinuxKittyTerminalDriver.class);
          assertThat(context.containsBean("claudeSandboxCheck")).isTrue();
          assertThat(context.containsBean("claudeKeychain")).isFalse();
        });
  }

  @Test
  void startsWithNoTerminalNotifierKeepAwakeEditorOrKittyWhereEachIsChosenNone(@TempDir Path root)
      throws Exception {
    ShippedRoot.copyInto(root);

    shipped
        .withPropertyValues(
            "orchestrator.root=" + root,
            "orchestrator.config-file=" + root.resolve("jagt.yml"),
            "orchestrator.state-file=" + root.resolve("state.json"),
            "orchestrator.platform=linux",
            "orchestrator.terminal=none",
            "orchestrator.notifier=none",
            "orchestrator.keep-awake=none")
        .run(context -> {
          assertThat(context.getBean(TerminalDriver.class)).isInstanceOf(NoTerminalDriver.class);
          assertThat(context.getBean(UserNotifier.class)).isInstanceOf(NoNotifier.class);
          assertThat(context.getBean(EditorDriver.class)).isInstanceOf(NoEditorDriver.class);
          assertThat(context.getBeansOfType(KeepAwake.class)).isEmpty();
          assertThat(context.getBeansOfType(AbstractKittyTerminalDriver.class)).isEmpty();
        });
  }
}
