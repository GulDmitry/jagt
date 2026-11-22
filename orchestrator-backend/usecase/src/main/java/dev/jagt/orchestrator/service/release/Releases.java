package dev.jagt.orchestrator.service.release;

import dev.jagt.orchestrator.port.host.CurrentPointer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Keeps the newer verified release found beside the running one, and points {@code current} at it
 * once its jar still matches the checksum it was kept with.
 */
@Service
@Slf4j
public class Releases {

  /** Why {@code current} stayed where it was. */
  public enum Refusal {
    NOT_KEPT,
    NO_CHECKSUM,
    MISMATCH,
    NOT_POINTED
  }

  /** What a switch came to. */
  public sealed interface Outcome {}

  /** {@code current} names the version. */
  public record Switched(String version) implements Outcome {}

  /** {@code current} is unchanged. */
  public record Refused(Refusal reason) implements Outcome {}

  /** A verified release waiting under {@code <root>/versions/<version>/}. */
  public record Kept(Path root, String version) {}

  private final CurrentPointer pointer;
  private volatile Optional<Kept> kept = Optional.empty();

  public Releases(CurrentPointer pointer) {
    this.pointer = pointer;
  }

  /** The newer release the last check found kept and verified; empty until one is. */
  public Optional<Kept> kept() {
    return kept;
  }

  public void keep(Path root, String version) {
    kept = Optional.of(new Kept(root, version));
  }

  public void keepNone() {
    kept = Optional.empty();
  }

  /** {@code current} names the version now, so it no longer waits. */
  public void switched(String version) {
    kept = kept.filter(waiting -> !waiting.version().equals(version));
  }

  public Outcome switchTo(Path root, String version) {
    Path folder = root.resolve(Versions.DIR).resolve(version);
    Path jar = folder.resolve(Versions.JAR);
    Path sum = folder.resolve(Versions.SUM);
    if (!Versions.valid(version) || !Files.isRegularFile(jar)) {
      return refused(version, Refusal.NOT_KEPT, null);
    }
    try {
      if (!Files.isRegularFile(sum)) {
        return refused(version, Refusal.NO_CHECKSUM, null);
      }
      if (!Sha256.of(jar).equalsIgnoreCase(Sha256.listed(sum, Versions.JAR))) {
        return refused(version, Refusal.MISMATCH, null);
      }
      pointer.point(root, version);
    } catch (IOException e) {
      return refused(version, Refusal.NOT_POINTED, e);
    }
    log.atInfo().setMessage("release switched").addKeyValue("ref", version).log();
    return new Switched(version);
  }

  private static Refused refused(String version, Refusal reason, @Nullable IOException cause) {
    log.atWarn()
        .setMessage("release switch refused")
        .addKeyValue("ref", version)
        .addKeyValue("reason", reason)
        .addKeyValue("cause", cause == null ? reason.name() : cause.toString())
        .addKeyValue("effect", "current names the version it named before")
        .setCause(cause)
        .log();
    return new Refused(reason);
  }
}
