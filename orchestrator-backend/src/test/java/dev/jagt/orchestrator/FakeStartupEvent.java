package dev.jagt.orchestrator;

import org.springframework.context.ApplicationEvent;

class FakeStartupEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1L;

  FakeStartupEvent() {
    super("startup");
  }
}
