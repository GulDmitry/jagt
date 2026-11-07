package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.BuildTools;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RepositoryBuildTools implements BuildTools {

  @Override
  public List<String> testCommand(Path checkout) {
    if (Files.exists(checkout.resolve("gradlew"))) {
      return List.of("./gradlew", "test");
    }
    if (Files.exists(checkout.resolve("mvnw"))) {
      return List.of("./mvnw", "test");
    }
    if (Files.exists(checkout.resolve("package.json"))) {
      return List.of("npm", "test");
    }
    return hasTestTarget(checkout.resolve("Makefile")) ? List.of("make", "test") : List.of();
  }

  private static boolean hasTestTarget(Path makefile) {
    try {
      return Files.isRegularFile(makefile)
          && Files.readString(makefile, StandardCharsets.UTF_8)
              .lines()
              .anyMatch(line -> line.startsWith("test:"));
    } catch (IOException unreadable) {
      return false;
    }
  }
}
