package dev.jagt.orchestrator.job.codereview.signals;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the notes and replies jagt posted were rated and resolved as, and why, one UTC line each,
 * appended and never rewritten; {@code brief} is the digest of the brief a note was written under,
 * blank for a reply.
 */
@Component
@RequiredArgsConstructor
public class ReviewStats {

  public static final String NOTE = "note";

  public static final String REPLY = "reply";

  /**
   * {@code kind} is {@code note} for the code reviewer's and {@code reply} for an answer the
   * Master's ship posted; {@code resolution} is {@code fixed} where the host says the thread
   * resolved, else unknown; {@code reason} is what humans replied under the note with their
   * reactions, {@code unknown} for a thumbs down nobody explained, blank otherwise.
   */
  public record Rating(int likes, int dislikes, String resolution, String reason) {}

  public record Line(
      String at,
      String request,
      String note,
      String kind,
      @JsonUnwrapped Rating rating,
      String brief) {}

  private static final JsonMapper MAPPER = new JsonMapper();

  private final OrchestratorPaths paths;
  private final Clock clock;

  public synchronized void append(
      String request, String note, String kind, Rating rating, String brief) {
    Path file = paths.layout().codeReviewStats();
    Line line = new Line(Instant.now(clock).toString(), request, note, kind, rating, brief);
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(
          file,
          MAPPER.writeValueAsString(line) + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException unwritten) {
      throw new UncheckedIOException(unwritten);
    }
  }
}
