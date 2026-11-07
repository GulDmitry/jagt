package dev.jagt.orchestrator.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A link is foreign where its repository's remote does not hold it. A repository whose remote is
 * unknown or names no host judges nothing.
 */
public final class ForeignLinks {

  /** A link {@code project}'s remote does not hold; {@code remote} is that remote. */
  public record Foreign(String project, String url, String remote) {}

  /**
   * What stays of a report's links once the foreign ones are out; {@code url} is null where none
   * stays.
   */
  public record Judged(Map<String, String> byProject, @Nullable String url, List<Foreign> foreign) {

    public boolean refusedAll() {
      return !foreign.isEmpty() && url == null;
    }
  }

  private ForeignLinks() {}

  /** {@code byProject}, or {@code url} on the primary repository where there is no map. */
  public static Judged judge(TaskState task, Map<String, String> byProject, @Nullable String url) {
    Map<String, String> asked =
        byProject.isEmpty() && url != null && task.primary().project() != null
            ? Map.of(task.primary().project(), url)
            : byProject;
    Map<String, String> kept = new LinkedHashMap<>();
    List<Foreign> foreign = new ArrayList<>();
    asked.forEach(
        (project, link) ->
            foreignRemote(task, project, link)
                .ifPresentOrElse(
                    remote -> foreign.add(new Foreign(project, link, remote)),
                    () -> kept.put(project, link)));
    if (foreign.isEmpty()) {
      return new Judged(byProject, url, List.of());
    }
    String stays =
        kept.isEmpty() ? null : kept.containsValue(url) ? url : kept.values().iterator().next();
    return new Judged(byProject.isEmpty() ? byProject : kept, stays, foreign);
  }

  /** {@code task} without the requests its repositories' remotes do not hold. */
  public static TaskState cleared(TaskState task) {
    TaskState cleared = task;
    for (Foreign foreign : foreignIn(task)) {
      cleared = cleared.withoutRequest(foreign.project());
    }
    return cleared;
  }

  /** Every request {@code task} holds that its repository's remote does not. */
  public static List<Foreign> foreignIn(TaskState task) {
    List<Foreign> foreign = new ArrayList<>();
    for (TaskRepo repo : task.repos()) {
      String project = repo.project();
      String link = repo.mrUrl();
      if (project != null && link != null) {
        foreignRemote(task, project, link)
            .ifPresent(remote -> foreign.add(new Foreign(project, link, remote)));
      }
    }
    return foreign;
  }

  private static Optional<String> foreignRemote(
      TaskState task, String project, @Nullable String link) {
    return task.repo(project)
        .map(TaskRepo::remoteUrl)
        .filter(
            remote -> GitRemote.hasHost(remote) && link != null && !GitRemote.holds(remote, link));
  }
}
