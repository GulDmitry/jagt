package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.With;

@With
public record TestServerRunBuilder(Path root) {

  public static TestServerRunBuilder serverRun(Path root) {
    return new TestServerRunBuilder(root);
  }

  public ServerRun build(JagtEngine jagt, StandSlots slots) throws IOException {
    Files.createDirectories(root);
    StandTeardown teardown =
        TestTeardownBuilder.teardown(Objects.requireNonNull(root.getParent())).build(slots);
    return new ServerRun(root, StandMarker.started("server"), jagt, teardown);
  }
}
