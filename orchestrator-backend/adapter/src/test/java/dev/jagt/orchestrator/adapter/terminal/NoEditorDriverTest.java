package dev.jagt.orchestrator.adapter.terminal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NoEditorDriverTest {

  @Test
  void refusesToOpenWorktreeNamingTheKeyThatEnablesTheEditor() {
    assertThatThrownBy(() -> new NoEditorDriver().open(Path.of("/work/abc-42")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("set `editor` in jagt.yml to `cli`");
  }

  @Test
  void refusesToOpenDiffNamingTheKeyThatEnablesTheEditor() {
    assertThatThrownBy(() -> new NoEditorDriver().openDiff(Path.of("/a"), Path.of("/b")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("set `editor` in jagt.yml to `cli`");
  }
}
