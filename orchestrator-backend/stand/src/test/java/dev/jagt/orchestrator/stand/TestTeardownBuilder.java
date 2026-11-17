package dev.jagt.orchestrator.stand;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import lombok.With;

@With
public record TestTeardownBuilder(Path base, Duration poll) {

  public static TestTeardownBuilder teardown(Path base) {
    return new TestTeardownBuilder(base, Duration.ofMillis(1));
  }

  public StandTeardown build(StandSlots slots) {
    return new StandTeardown(
        new StandArchive(
            base.resolve("runs"),
            base.resolve("recordings"),
            base.resolve("failed"),
            (run, result) -> {},
            parent -> new StandTraces(parent, parent.resolve("claude.json"), parent.resolve("p"))),
        slots,
        new PrintStream(OutputStream.nullOutputStream()),
        poll);
  }
}
