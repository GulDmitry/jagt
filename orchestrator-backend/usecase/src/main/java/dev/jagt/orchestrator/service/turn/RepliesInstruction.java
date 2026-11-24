package dev.jagt.orchestrator.service.turn;

import dev.jagt.orchestrator.service.ConfigFile;
import java.util.Optional;

public interface RepliesInstruction {

  /** The replies instruction a ship is held to, empty where nothing holds it. */
  Optional<String> repliesStep(String taskId, ConfigFile config);

  /** Whether the ship posts the replies drafted to a person's note. */
  boolean postsPersonReplies(ConfigFile config);
}
