package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ShippedCheckersTest {

  @Test
  void shippedCheckersAreTheRoleFoldersWithRoleDefinition() throws Exception {
    try (Stream<Path> folders = Files.list(Path.of("../../roles"))) {
      Set<String> withDefinition =
          folders
              .filter(folder -> Files.exists(folder.resolve("role.yml.dist")))
              .map(folder -> folder.getFileName().toString())
              .collect(Collectors.toSet());

      assertThat(Checkers.SHIPPED).isEqualTo(withDefinition);
    }
  }
}
