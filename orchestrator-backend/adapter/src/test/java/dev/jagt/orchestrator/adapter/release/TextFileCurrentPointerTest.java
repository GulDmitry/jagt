package dev.jagt.orchestrator.adapter.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextFileCurrentPointerTest {

  private final TextFileCurrentPointer pointer = new TextFileCurrentPointer();

  @Test
  void rewritesTheFileWithTheNewVersion(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("current"), "0.24.1");

    pointer.point(root, "0.25.0");

    assertThat(root.resolve("current")).hasContent("0.25.0");
    assertThat(root.resolve("current.next")).doesNotExist();
  }

  @Test
  void createsTheFileWhenNoneExists(@TempDir Path root) throws Exception {
    pointer.point(root, "0.25.0");

    assertThat(root.resolve("current")).hasContent("0.25.0");
  }
}
