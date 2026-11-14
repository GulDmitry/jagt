package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.task.repo.Remote;
import java.nio.file.Path;
import java.util.Optional;

/** Which remote a repository shares its branches through. */
@FunctionalInterface
public interface Remotes {

  Optional<Remote> of(Path repository);
}
