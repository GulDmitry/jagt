package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SuitesTapNobodyTest {

  @Test
  void everyBootedSuiteMocksTheDesktopNotifierButTheOnePinningItsWiring() {
    List<String> tapping =
        SourceRoots.javaFiles()
            .filter(path -> SourceRoots.read(path).matches("(?sm).*^@SpringBootTest.*"))
            .filter(path ->
                !SourceRoots.read(path)
                    .matches(
                        "(?s).*(@MockitoBean\\s+(private\\s+)?UserNotifier\\b"
                            + "|@MockitoBean\\(types\\s*=\\s*\\{[^}]*\\bUserNotifier\\.class)"
                            + ".*"))
            .map(path -> path.getFileName().toString())
            .filter(name ->
                !List.of("LinuxProfileContextTest.java", "NoneProfileContextTest.java")
                    .contains(name))
            .sorted()
            .toList();

    assertThat(tapping).isEmpty();
  }
}
