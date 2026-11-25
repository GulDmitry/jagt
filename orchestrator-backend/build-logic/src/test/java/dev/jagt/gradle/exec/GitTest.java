package dev.jagt.gradle.exec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitTest {

  @Test
  void readsAnOutputLargerThanThePipeBufferWithoutHanging(@TempDir Path repo) throws Exception {
    Files.writeString(repo.resolve("big.txt"), "line\n".repeat(50_000));
    new ProcessBuilder("git", "-C", repo.toString(), "init", "-q").start().waitFor();
    new ProcessBuilder("git", "-C", repo.toString(), "add", "big.txt").start().waitFor();

    List<String> lines = new Git(repo).lines("show", ":big.txt");

    assertThat(lines).hasSize(50_000);
  }
}
