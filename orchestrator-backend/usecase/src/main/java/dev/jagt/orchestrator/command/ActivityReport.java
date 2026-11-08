package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskStatus;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Every status change of every task, live or finished, newest first. A finished task's history ends
 * where `done` retired it, so its last line is stamped at the retirement.
 */
@Service
@RequiredArgsConstructor
public class ActivityReport {

  private static final int MAX_LINES = 200;
  private static final Pattern WORDS = Pattern.compile("\\s+");
  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private record Change(
      long at,
      String task,
      List<String> projects,
      boolean first,
      @Nullable TaskStatus from,
      TaskStatus to,
      @Nullable ActionOrigin origin) {}

  private final StateService state;
  private final FinishedTasks finished;

  private record Filter(Optional<ActivityKind> kind, Optional<String> project) {

    static Optional<Filter> parse(String tail) {
      Optional<ActivityKind> kind = Optional.empty();
      Optional<String> project = Optional.empty();
      for (String word : WORDS.splitAsStream(tail.strip()).filter(w -> !w.isEmpty()).toList()) {
        Optional<ActivityKind> named = ActivityKind.named(word);
        if (named.isPresent() && kind.isEmpty()) {
          kind = named;
        } else if (named.isEmpty() && project.isEmpty()) {
          project = Optional.of(word);
        } else {
          return Optional.empty();
        }
      }
      return Optional.of(new Filter(kind, project));
    }

    boolean keeps(Change change) {
      boolean ofKind = kind.map(found -> found.covers(change.first(), change.to())).orElse(true);
      boolean inProject =
          project
              .map(found -> change.projects().stream().anyMatch(found::equalsIgnoreCase))
              .orElse(true);
      return ofKind && inProject;
    }

    String words() {
      return kind.map(found -> " of kind " + found.word()).orElse("")
          + project.map(found -> " in project " + found).orElse("");
    }
  }

  /** {@code tail} is an optional kind and an optional project key, in either order. */
  public String render(String tail) {
    Optional<Filter> parsed = Filter.parse(tail);
    if (parsed.isEmpty()) {
      return usage();
    }
    Filter filter = parsed.get();
    List<Change> shown = changes().stream().filter(filter::keeps).toList();
    if (shown.isEmpty()) {
      return "no status change matches" + filter.words() + ".\n";
    }
    return "status changes"
        + filter.words()
        + (shown.size() > MAX_LINES ? " — the newest " + MAX_LINES + " of " + shown.size() : "")
        + ", newest first (UTC):\n\n"
        + shown.stream()
            .limit(MAX_LINES)
            .map(ActivityReport::line)
            .collect(Collectors.joining("\n"))
        + "\n";
  }

  private List<Change> changes() {
    List<Change> changes = new ArrayList<>();
    state.tasks().forEach((id, task) -> changes.addAll(of(id, task.projects(), task.history())));
    for (FinishedTask task : finished.all()) {
      String id = task.identity().id();
      List<StatusChange> history = task.course().history();
      changes.addAll(of(id, task.identity().projects(), history));
      if (history.isEmpty() || history.getLast().status() != TaskStatus.DONE) {
        changes.add(
            new Change(
                task.finishedAt(),
                id,
                task.identity().projects(),
                history.isEmpty(),
                history.isEmpty() ? null : history.getLast().status(),
                TaskStatus.DONE,
                null));
      }
    }
    changes.sort(Comparator.comparingLong(Change::at).reversed());
    return changes;
  }

  private static List<Change> of(String id, List<String> projects, List<StatusChange> history) {
    List<Change> changes = new ArrayList<>();
    for (int i = 0; i < history.size(); i++) {
      StatusChange step = history.get(i);
      changes.add(
          new Change(
              step.at(),
              id,
              projects,
              i == 0,
              i == 0 ? null : history.get(i - 1).status(),
              step.status(),
              step.origin()));
    }
    return changes;
  }

  private static String line(Change change) {
    return "  %s  %-14s %s → %s  %s"
        .formatted(
            WHEN.format(Instant.ofEpochMilli(change.at())),
            change.task(),
            change.from() == null ? "-" : change.from().label(),
            change.to().label(),
            change.origin() == null ? "-" : change.origin().label());
  }

  private static String usage() {
    return "usage: activity [%s] [<project>]\n"
        .formatted(
            Arrays.stream(ActivityKind.values())
                .map(ActivityKind::word)
                .collect(Collectors.joining("|")));
  }
}
