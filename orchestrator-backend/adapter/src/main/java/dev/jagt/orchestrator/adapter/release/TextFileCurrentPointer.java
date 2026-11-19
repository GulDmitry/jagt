package dev.jagt.orchestrator.adapter.release;

import dev.jagt.orchestrator.port.host.CurrentPointer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** A text file naming the version, as the Windows installer writes it; no symlink needs rights. */
@Component
@ConditionalOnProperty(name = "orchestrator.platform", havingValue = "windows")
class TextFileCurrentPointer implements CurrentPointer {

  @Override
  public void point(Path root, String version) throws IOException {
    Path next = root.resolve("current.next");
    Files.writeString(next, version, StandardCharsets.US_ASCII);
    Files.move(next, root.resolve("current"), StandardCopyOption.REPLACE_EXISTING);
  }
}
