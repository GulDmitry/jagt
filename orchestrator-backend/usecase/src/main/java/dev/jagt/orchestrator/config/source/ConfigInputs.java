package dev.jagt.orchestrator.config.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** What a configuration value may be read from besides {@code jagt.yml}. */
public interface ConfigInputs {

  Map<String, String> environment();

  String read(Path file) throws IOException;
}
