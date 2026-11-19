package dev.jagt.orchestrator.adapter.release;

import dev.jagt.orchestrator.port.host.ReleaseFeed;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orchestrator.release-feed.kind", havingValue = "none")
class NoReleaseFeed implements ReleaseFeed {

  @Override
  public String driver() {
    return "none";
  }

  @Override
  public String host() {
    return "";
  }

  @Override
  public Optional<String> latest() {
    return Optional.empty();
  }

  @Override
  public void jar(String version, Path target) throws IOException {
    throw new IOException("no release feed is configured");
  }

  @Override
  public void checksums(String version, Path target) throws IOException {
    throw new IOException("no release feed is configured");
  }
}
