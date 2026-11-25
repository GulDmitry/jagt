package dev.jagt.orchestrator.adapter.herdr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** One request line to a herdr session's socket and the one answer line back. */
public interface HerdrWire {

  /** The answer line; empty where no server listens on the socket. */
  Optional<String> exchange(Path socket, String request) throws IOException;
}
