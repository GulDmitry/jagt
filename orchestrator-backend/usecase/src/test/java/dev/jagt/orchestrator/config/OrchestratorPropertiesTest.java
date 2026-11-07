package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OrchestratorPropertiesTest {

  @Test
  void keepsTheCommandExactlyAsItWasConfigured() {
    List<String> command =
        ToolchainProperties.defaults()
            .withEditorDiffCommand(List.of("sh", "diff"))
            .editorDiffCommand();

    assertThat(command).containsExactly("sh", "diff");
  }

  @Test
  void bothRecordsBindTheirKeysFromTheSameOrchestratorPrefix() {
    Binder binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of(
                    "orchestrator.root", "/jagt",
                    "orchestrator.tmux-command", "tmux",
                    "orchestrator.tmux-socket", "jagt-sock",
                    "orchestrator.editor-command", "ed",
                    "orchestrator.editor-diff-command", "diff")));

    OrchestratorProperties orchestrator =
        binder.bind("orchestrator", Bindable.of(OrchestratorProperties.class)).get();
    ToolchainProperties toolchain =
        binder.bind("orchestrator", Bindable.of(ToolchainProperties.class)).get();

    assertThat(orchestrator.root()).isEqualTo("/jagt");
    assertThat(toolchain)
        .isEqualTo(new ToolchainProperties(List.of("ed"), List.of("diff"), "tmux", "jagt-sock"));
  }
}
