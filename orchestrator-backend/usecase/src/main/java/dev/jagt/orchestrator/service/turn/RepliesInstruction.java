package dev.jagt.orchestrator.service.turn;

import dev.jagt.orchestrator.service.ConfigFile;
import java.util.Optional;

@FunctionalInterface
public interface RepliesInstruction {

  /** The replies instruction a ship is held to, empty where nothing holds it. */
  Optional<String> repliesStep(String taskId, ConfigFile config);
}
