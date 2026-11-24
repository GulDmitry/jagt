package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The {@code Author:} line of each thread of a round, given by jagt from the host's own notes:
 * {@code person}, {@code bot} or {@code reviewer}, then the newest note's author. An author the
 * host does not name in a read that succeeded is a person; where the read failed the kind is {@code
 * unknown}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ThreadAuthors {

  public static final String REVIEWER = "reviewer";

  private static final String UNKNOWN = "unknown";

  private final StateService stateService;
  private final RequestNotes notes;

  public List<String> lines(
      String taskId, RoundSource source, String requestUrl, List<String> threads) {
    if (source == RoundSource.TREE) {
      return threads.stream().map(thread -> "Author: " + REVIEWER).toList();
    }
    Optional<List<RequestNote>> known = read(taskId, requestUrl);
    return threads.stream()
        .map(thread -> known.map(list -> line(newestAuthor(thread), list)).orElse(unknown(thread)))
        .toList();
  }

  private static String unknown(String thread) {
    return "Author: " + UNKNOWN + " " + newestAuthor(thread);
  }

  private static String line(String name, List<RequestNote> known) {
    AuthorKind kind =
        known.stream()
            .filter(note -> normal(note.author()).equals(normal(name)))
            .map(RequestNote::kind)
            .findFirst()
            .orElse(AuthorKind.PERSON);
    return "Author: " + word(kind) + " " + name;
  }

  private static String word(AuthorKind kind) {
    return switch (kind) {
      case BOT -> "bot";
      case ACTOR -> REVIEWER;
      case PERSON -> "person";
    };
  }

  private Optional<List<RequestNote>> read(String taskId, String requestUrl) {
    List<String> projects =
        stateService
            .task(taskId)
            .map(task ->
                task.repos().stream()
                    .map(TaskRepo::project)
                    .filter(project -> project != null && !project.isBlank())
                    .toList())
            .orElse(List.of());
    for (String project : projects) {
      Answer<List<RequestNote>> read = notes.notes(project, requestUrl);
      if (read.facts().isPresent()) {
        return read.facts();
      }
      log.atWarn()
          .setMessage("thread authors unread")
          .addKeyValue("task", taskId)
          .addKeyValue("request", requestUrl)
          .addKeyValue("cause", read.failure())
          .addKeyValue("effect", "its threads carry the author kind unknown")
          .log();
    }
    return Optional.empty();
  }

  private static String newestAuthor(String thread) {
    String newest = thread.strip().lines().reduce((first, second) -> second).orElse("");
    int colon = newest.indexOf(':');
    String who = colon > 0 ? newest.substring(0, colon) : newest;
    int file = who.indexOf(" (");
    return (file > 0 ? who.substring(0, file) : who).strip();
  }

  private static String normal(String author) {
    String stripped = author.strip();
    return (stripped.startsWith("@") ? stripped.substring(1) : stripped).toLowerCase(Locale.ROOT);
  }
}
