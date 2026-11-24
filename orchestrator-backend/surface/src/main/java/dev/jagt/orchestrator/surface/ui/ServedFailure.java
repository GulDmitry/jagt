package dev.jagt.orchestrator.surface.ui;

public interface ServedFailure extends AutoCloseable {

  int port();

  void awaitSave() throws InterruptedException;

  @Override
  void close();
}
