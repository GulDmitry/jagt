package dev.jagt.orchestrator.surface.ui;

import java.nio.file.Path;
import java.util.Optional;

@FunctionalInterface
public interface FailureListener {

  /** Empty where the port cannot be bound. */
  Optional<? extends ServedFailure> listen(int port, Path configFile, String problems);
}
