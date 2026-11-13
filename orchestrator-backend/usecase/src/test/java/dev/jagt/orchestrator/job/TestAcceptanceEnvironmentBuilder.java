package dev.jagt.orchestrator.job;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.With;

@With
public record TestAcceptanceEnvironmentBuilder(String url) {

  public static TestAcceptanceEnvironmentBuilder acceptanceEnvironment() {
    return new TestAcceptanceEnvironmentBuilder("https://stage.example.com");
  }

  public Path build(Path root) {
    try {
      Files.createDirectories(root.resolve("roles/acceptance"));
      return Files.writeString(
          root.resolve("roles/acceptance/role.yml"), "environments: [{url: \"" + url + "\"}]\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
