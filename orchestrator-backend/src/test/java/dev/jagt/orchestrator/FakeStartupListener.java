package dev.jagt.orchestrator;

import org.springframework.context.ApplicationListener;

record FakeStartupListener(Runnable onEvent) implements ApplicationListener<FakeStartupEvent> {

  @Override
  public void onApplicationEvent(FakeStartupEvent event) {
    onEvent.run();
  }
}
