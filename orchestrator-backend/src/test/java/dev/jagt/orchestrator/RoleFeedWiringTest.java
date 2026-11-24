package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RoleFeedWiringTest {

  private static Stream<String> modelRoles() {
    return Stream.concat(Stream.of("master"), RoleModules.CHECKERS.keySet().stream());
  }

  @ParameterizedTest
  @MethodSource("modelRoles")
  void everyRoleModuleHasItsOwnFeedAppenderInTheLogConfiguration(String role) throws IOException {
    String logback = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

    assertThat(logback)
        .contains(
            "<appender name=\"ROLE_"
                + role
                + "\" class=\"dev.jagt.orchestrator.surface.ui.log.RecentLines\">")
        .contains("<appender-ref ref=\"ROLE_" + role + "\"/>");
  }
}
