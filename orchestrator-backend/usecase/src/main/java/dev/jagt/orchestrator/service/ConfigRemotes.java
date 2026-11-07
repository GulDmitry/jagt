package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.Remote;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The {@code remote} of the configured project at that repository, read on every call; {@code
 * origin} for any other.
 */
@Component
@RequiredArgsConstructor
public class ConfigRemotes implements Remotes {

  private final ConfigService configService;

  @Override
  public Optional<Remote> of(Path repository) {
    Path wanted = real(repository);
    return configService.load().projects().values().stream()
        .filter(project -> project.path() != null && real(Path.of(project.path())).equals(wanted))
        .findFirst()
        .map(ProjectConfig::gitRemote);
  }

  private static Path real(Path path) {
    try {
      return path.toRealPath();
    } catch (IOException unresolvable) {
      return path.toAbsolutePath().normalize();
    }
  }
}
