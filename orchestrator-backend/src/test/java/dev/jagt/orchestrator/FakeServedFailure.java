package dev.jagt.orchestrator;

import dev.jagt.orchestrator.surface.ui.ServedFailure;

record FakeServedFailure(boolean saved) implements ServedFailure {

  @Override
  public int port() {
    return 8290;
  }

  @Override
  public void awaitSave() throws InterruptedException {
    if (!saved) {
      throw new InterruptedException();
    }
  }

  @Override
  public void close() {}
}
