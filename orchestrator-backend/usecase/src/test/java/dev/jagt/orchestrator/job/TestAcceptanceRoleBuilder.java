package dev.jagt.orchestrator.job;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.With;

@With
public record TestAcceptanceRoleBuilder(String trigger) {

  public static TestAcceptanceRoleBuilder acceptanceRole() {
    return new TestAcceptanceRoleBuilder("deployed, pipelines-green, environment-named");
  }

  public Path build(Path root) {
    try {
      return Files.writeString(
          root.resolve("role.yml"),
          """
          id: acceptance
          name: acceptance
          model: sonnet
          trigger: [%s]
          oncePer: deploy
          outcomes: [round]
          tools:
            shell: ["git log*"]
          answer:
            - required: failure
          decline:
            - required: failure
          """
              .formatted(trigger));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
