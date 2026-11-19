package dev.jagt.orchestrator.port.host;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** Where jagt's releases are published; one driver per vendor, chosen in {@code jagt.yml}. */
public interface ReleaseFeed {

  /** The driver's name, as the reach mark shows it. */
  String driver();

  /** The host the feed is read from, as the reach mark shows it; empty where none is read. */
  String host();

  /** The newest published version without a leading {@code v}; empty when the feed names none. */
  Optional<String> latest() throws IOException;

  /** Writes the release's jar to {@code target}. */
  void jar(String version, Path target) throws IOException;

  /** Writes the release's {@code sha256sum} lines to {@code target}. */
  void checksums(String version, Path target) throws IOException;
}
