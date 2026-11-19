package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NoByHandTextTest {

  @Test
  void noConfigurationFileOrItsWriterTellsTheHumanToEditByHand() throws Exception {
    List<Path> files;
    try (Stream<Path> writer =
        Files.walk(Path.of("usecase/src/main/java/dev/jagt/orchestrator/service/project"))) {
      files =
          Stream.concat(
                  writer.filter(Files::isRegularFile),
                  Stream.of(
                      Path.of("../jagt.desktop.yml.dist"),
                      Path.of("../jagt.server.yml.dist"),
                      Path.of("../examples/kubernetes.yml")))
              .toList();
    }

    assertThat(files)
        .isNotEmpty()
        .allSatisfy(file ->
            assertThat(Files.readString(file).toLowerCase(java.util.Locale.ROOT))
                .doesNotContain("by hand"));
  }
}
