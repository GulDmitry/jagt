package dev.jagt.orchestrator.adapter.release;

import dev.jagt.orchestrator.port.host.CurrentPointer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

/** Keeps the installer's form: the Windows launcher reads a version file, the shell one a link. */
@Component
class InstalledCurrentPointer implements CurrentPointer {

  @Override
  public void point(Path root, String version) throws IOException {
    Path current = root.resolve("current");
    Path next = root.resolve("current.next");
    Files.deleteIfExists(next);
    if (Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
      Files.writeString(next, version, StandardCharsets.US_ASCII);
      Files.move(next, current, StandardCopyOption.REPLACE_EXISTING);
    } else {
      Files.createSymbolicLink(next, Path.of("versions", version));
      Files.move(next, current, StandardCopyOption.ATOMIC_MOVE);
    }
  }
}
