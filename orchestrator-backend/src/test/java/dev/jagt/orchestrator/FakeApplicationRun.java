package dev.jagt.orchestrator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

final class FakeApplicationRun implements Consumer<String[]> {

  private final Deque<Throwable> outcomes = new ArrayDeque<>();
  private int runs;

  FakeApplicationRun failing(Throwable failure) {
    outcomes.add(failure);
    return this;
  }

  int runs() {
    return runs;
  }

  @Override
  public void accept(String[] launch) {
    runs++;
    Throwable failure = outcomes.poll();
    if (failure instanceof Error error) {
      throw error;
    }
    if (failure instanceof RuntimeException runtime) {
      throw runtime;
    }
  }
}
