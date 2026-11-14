package dev.jagt.orchestrator;

import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;

record FakeStateWrittenListener(Runnable onEvent)
    implements ApplicationListener<PayloadApplicationEvent<StateWritten>> {

  @Override
  public void onApplicationEvent(PayloadApplicationEvent<StateWritten> event) {
    onEvent.run();
  }
}
