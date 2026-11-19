package dev.jagt.orchestrator.adapter.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SymlinkCurrentPointerTest {

  private final SymlinkCurrentPointer pointer = new SymlinkCurrentPointer();

  @Test
  void replacesTheLinkWithOneNamingTheNewVersion(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("versions/0.24.1"));
    Files.createDirectories(root.resolve("versions/0.25.0"));
    Files.createSymbolicLink(root.resolve("current"), Path.of("versions", "0.24.1"));

    pointer.point(root, "0.25.0");

    assertThat(Files.readSymbolicLink(root.resolve("current")))
        .isEqualTo(Path.of("versions/0.25.0"));
    assertThat(root.resolve("versions/0.24.1")).isDirectory();
    assertThat(root.resolve("current.next")).doesNotExist();
  }

  @Test
  void createsTheLinkWhenNoneExists(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("versions/0.25.0"));

    pointer.point(root, "0.25.0");

    assertThat(Files.readSymbolicLink(root.resolve("current")))
        .isEqualTo(Path.of("versions/0.25.0"));
  }
}
