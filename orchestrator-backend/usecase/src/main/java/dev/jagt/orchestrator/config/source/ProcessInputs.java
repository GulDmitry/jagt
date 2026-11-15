package dev.jagt.orchestrator.config.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProcessInputs implements ConfigInputs {

  @Override
  public Map<String, String> environment() {
    return System.getenv();
  }

  @Override
  public String read(Path file) throws IOException {
    return Files.readString(file);
  }
}
