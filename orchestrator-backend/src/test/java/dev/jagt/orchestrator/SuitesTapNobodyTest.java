package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuitesTapNobodyTest {

  @Test
  void everyBootedSuiteMocksTheDesktopNotifierButTheOnePinningItsWiring() {
    List<String> tapping =
        SourceRoots.javaFiles()
            .filter(path -> read(path).matches("(?sm).*^@SpringBootTest.*"))
              .filter(
                  path ->
                      !read(path)
                          .matches(
                              "(?s).*(@MockitoBean\\s+(private\\s+)?UserNotifier\\b"
                                  + "|@MockitoBean\\(types\\s*=\\s*\\{[^}]*\\bUserNotifier\\.class)"
                                  + ".*"))
              .map(path -> path.getFileName().toString())
              .filter(name -> !name.equals("LinuxProfileContextTest.java"))
              .sorted()
            .toList();

    assertThat(tapping).isEmpty();
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
