package fitness.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SourceRootWalkTest {

  @Test
  void passesOverAFileDeletedWhileTheWalkRuns() throws IOException {
    var walk = new SourceRootWalk(path -> true);

    FileVisitResult result =
        walk.visitFileFailed(Path.of(".attach_pid42"), new NoSuchFileException(".attach_pid42"));

    assertThat(result).isEqualTo(FileVisitResult.CONTINUE);
  }

  @Test
  void failsOnAFileItCannotRead() {
    var walk = new SourceRootWalk(path -> true);

    assertThatThrownBy(
            () -> walk.visitFileFailed(Path.of("locked"), new AccessDeniedException("locked")))
        .isInstanceOf(AccessDeniedException.class);
  }
}
