package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.CurrentOrigin;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** The origin the entry point of this thread opened. */
@Component
public class ThreadOrigin implements CurrentOrigin {

  @Override
  public @Nullable ActionOrigin current() {
    return OriginContext.current();
  }
}
