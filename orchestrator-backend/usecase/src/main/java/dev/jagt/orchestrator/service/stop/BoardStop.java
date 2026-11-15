package dev.jagt.orchestrator.service.stop;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The flag that halts all unattended work; only the human's lift ends it, and a restart keeps it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BoardStop {

  /** {@code at} is epoch millis. */
  public record Standing(long at) {}

  private final OrchestratorPaths paths;
  private final Clock clock;

  public boolean stopped() {
    return Files.exists(paths.boardStopped());
  }

  public Optional<Standing> standing() {
    try {
      return Optional.of(
          new Standing(Long.parseLong(Files.readString(paths.boardStopped()).strip())));
    } catch (java.nio.file.NoSuchFileException none) {
      return Optional.empty();
    } catch (IOException | NumberFormatException unread) {
      return Optional.of(new Standing(0));
    }
  }

  /** True where it was not stopped before. */
  public synchronized boolean engage() {
    if (stopped()) {
      return false;
    }
    try {
      Files.writeString(paths.boardStopped(), Long.toString(clock.millis()));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + paths.boardStopped(), e);
    }
    log.atWarn()
        .setMessage("board stopped")
        .addKeyValue("effect", "jobs and intake wait for the human's lift")
        .log();
    return true;
  }

  /** True where it stood. */
  public synchronized boolean lift() {
    try {
      boolean was = Files.deleteIfExists(paths.boardStopped());
      log.atInfo().setMessage("board stop lifted").addKeyValue("wasStopped", was).log();
      return was;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot remove " + paths.boardStopped(), e);
    }
  }
}
