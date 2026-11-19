package dev.jagt.orchestrator.adapter.release;

import dev.jagt.orchestrator.port.host.CurrentPointer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** A symlink, swapped by renaming a new one over it, so a launcher never meets a missing link. */
@Component
@ConditionalOnExpression("'${orchestrator.platform:macos}' != 'windows'")
class SymlinkCurrentPointer implements CurrentPointer {

  @Override
  public void point(Path root, String version) throws IOException {
    Path next = root.resolve("current.next");
    Files.deleteIfExists(next);
    Files.createSymbolicLink(next, Path.of("versions", version));
    Files.move(next, root.resolve("current"), StandardCopyOption.ATOMIC_MOVE);
  }
}
