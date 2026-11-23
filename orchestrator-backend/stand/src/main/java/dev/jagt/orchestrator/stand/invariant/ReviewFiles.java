package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.stand.StandFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class ReviewFiles {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private ReviewFiles() {}

  static RunRecord.Reviews read(Path host) {
    return new RunRecord.Reviews(
        notes(host.resolve("notes")), approvals(host.resolve("approvals.log")));
  }

  private static List<RunRecord.Note> notes(Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(file -> file.toString().endsWith(".json"))
          .map(file -> note(MAPPER.readTree(file.toFile())))
          .sorted(Comparator.comparingInt(RunRecord.Note::id))
          .toList();
    } catch (IOException e) {
      throw new StandFailure("cannot list " + directory, e);
    }
  }

  private static RunRecord.Note note(JsonNode note) {
    return new RunRecord.Note(
        note.path("id").asInt(),
        note.path("pull").asInt(),
        note.path("kind").asString(),
        note.path("body").asString(),
        Instant.parse(note.path("at").asString()));
  }

  private static List<RunRecord.Approval> approvals(Path file) {
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    return StateFiles.lines(file)
        .filter(line -> !line.isBlank())
        .map(line -> line.split(" ", 2))
        .map(words -> new RunRecord.Approval(Instant.parse(words[0]), words[1]))
        .toList();
  }
}
