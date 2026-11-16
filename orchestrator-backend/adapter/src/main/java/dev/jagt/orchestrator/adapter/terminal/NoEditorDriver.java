package dev.jagt.orchestrator.adapter.terminal;

import dev.jagt.orchestrator.port.terminal.EditorDriver;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orchestrator.editor", havingValue = "none")
public class NoEditorDriver implements EditorDriver {

  static final String SENTENCE =
      "no editor is chosen: set `editor` in jagt.yml to `cli` and `editorCommand` to your launcher"
          + " to open a worktree.";

  @Override
  public boolean absent() {
    return true;
  }

  @Override
  public void open(Path path) {
    throw new IllegalStateException(SENTENCE);
  }

  @Override
  public void openDiff(Path left, Path right) {
    throw new IllegalStateException(SENTENCE);
  }
}
