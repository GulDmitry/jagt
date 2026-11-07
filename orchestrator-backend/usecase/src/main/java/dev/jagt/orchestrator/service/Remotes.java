package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Remote;
import java.nio.file.Path;
import java.util.Optional;

/** Which remote a repository shares its branches through. */
@FunctionalInterface
public interface Remotes {

  Optional<Remote> of(Path repository);
}
