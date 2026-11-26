package dev.jagt.orchestrator.config.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The top-level keys of jagt.yml that Spring's environment binds, no config record holding them.
 */
@Component
public class SpringReadKeys {

  private static final String RESOURCE = "/spring-read-keys.txt";

  public Set<String> names() {
    try (InputStream stream = SpringReadKeys.class.getResourceAsStream(RESOURCE)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException unreadable) {
      throw new UncheckedIOException(RESOURCE + " is unreadable", unreadable);
    }
  }
}
