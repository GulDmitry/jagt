package dev.jagt.orchestrator.service.release;

import dev.jagt.orchestrator.port.host.CurrentPointer;
import java.io.IOException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

final class FakeCurrentPointer implements CurrentPointer {

  private final boolean failing;
  @Nullable String pointedAt;

  FakeCurrentPointer(boolean failing) {
    this.failing = failing;
  }

  @Override
  public void point(Path root, String version) throws IOException {
    if (failing) {
      throw new IOException("read-only file system");
    }
    pointedAt = version;
  }
}
