package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.turn.ActionOrigin;
import org.jspecify.annotations.Nullable;

/** Who asked for the press being answered; null where no entry point said. */
@FunctionalInterface
public interface CurrentOrigin {

  @Nullable ActionOrigin current();
}
