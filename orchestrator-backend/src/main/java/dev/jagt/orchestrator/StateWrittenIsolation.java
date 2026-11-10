package dev.jagt.orchestrator;

import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

/** A listener failing on a written state neither fails the write nor stops the next listener. */
@Slf4j
public class StateWrittenIsolation extends SimpleApplicationEventMulticaster {

  @Override
  protected void invokeListener(ApplicationListener<?> listener, ApplicationEvent event) {
    if (!(event instanceof PayloadApplicationEvent<?> payload)
        || !(payload.getPayload() instanceof StateWritten)) {
      super.invokeListener(listener, event);
      return;
    }
    try {
      super.invokeListener(listener, event);
    } catch (RuntimeException e) {
      log.atWarn()
          .setMessage("state listener failed")
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "write completed")
          .addKeyValue("listener", listener.toString())
          .log();
    }
  }
}
