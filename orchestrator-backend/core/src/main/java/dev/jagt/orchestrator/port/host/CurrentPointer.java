package dev.jagt.orchestrator.port.host;

import java.io.IOException;
import java.nio.file.Path;

/** The install root's {@code current}, which the launcher follows to the version it runs. */
public interface CurrentPointer {

  /**
   * Makes {@code current} under {@code root} name {@code version}, kept under {@code versions/}. A
   * failure leaves {@code current} as it was.
   */
  void point(Path root, String version) throws IOException;
}
