package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EndlessShellLoopTest {

  @Test
  void everyEndlessShellLoopInTestSourcesHasItsJvmAsLifeline() {
    Pattern endless = Pattern.compile("while\\s+(:|true)\\s*;");

    assertThat(
            SourceRoots.ROOTS.stream()
                .filter(SourceRoots::isTest)
                .flatMap(SourceRoots::javaFilesUnder)
                .filter(file -> endless.matcher(SourceRoots.read(file)).find())
                .map(Path::toString)
                .toList())
        .isEmpty();
  }
}
