package dev.jagt.orchestrator;

import org.springframework.context.ApplicationEvent;

class FakeStartupEvent extends ApplicationEvent {

  FakeStartupEvent() {
    super("startup");
  }
}
