package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.config.source.ConfigInputs;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class FakeConfigInputs implements ConfigInputs {

  private final Map<String, String> environment = new HashMap<>();
  private final Map<Path, String> files = new HashMap<>();

  public FakeConfigInputs setting(String name, String value) {
    environment.put(name, value);
    return this;
  }

  public FakeConfigInputs unsetting(String name) {
    environment.remove(name);
    return this;
  }

  public FakeConfigInputs holding(String file, String content) {
    files.put(Path.of(file), content);
    return this;
  }

  @Override
  public Map<String, String> environment() {
    return Map.copyOf(environment);
  }

  @Override
  public String read(Path file) throws IOException {
    String content = files.get(file);
    if (content == null) {
      throw new FileNotFoundException(file.toString());
    }
    return content;
  }
}
