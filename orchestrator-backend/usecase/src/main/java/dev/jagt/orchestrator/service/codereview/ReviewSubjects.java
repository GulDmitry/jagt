package dev.jagt.orchestrator.service.codereview;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The requests to read: those of jagt's own tasks, and those the host names for review in each
 * project that has an address. A project whose listing failed is named, so nothing is ended on a
 * list that was never read.
 */
@Component
@RequiredArgsConstructor
public class ReviewSubjects {

  /** {@code failed} are the projects whose listing stopped. */
  public record Listing(List<Subject> subjects, Set<String> failed) {

    public boolean complete(String project) {
      return !failed.contains(project);
    }
  }

  private final ConfigService configService;
  private final StateService stateService;
  private final RequestNotes notes;

  public Listing list() {
    List<Subject> subjects = new ArrayList<>();
    Set<String> held = new HashSet<>();
    Set<String> failed = new HashSet<>();
    stateService
        .tasks()
        .forEach((taskId, task) ->
            task.repos().forEach(repo -> own(taskId, task, repo, subjects, held)));
    configService
        .load()
        .projects()
        .forEach((project, declared) -> others(project, declared, subjects, held, failed));
    return new Listing(List.copyOf(subjects), Set.copyOf(failed));
  }

  private static void own(
      String taskId, TaskState task, TaskRepo repo, List<Subject> subjects, Set<String> held) {
    String project = repo.project();
    boolean over = Boolean.TRUE.equals(repo.merged()) || Boolean.TRUE.equals(repo.hostMerged());
    if (project == null || over) {
      return;
    }
    task.reviewRequestOf(project)
        .ifPresent(url -> {
          held.add(url);
          subjects.add(new Subject(project, url, String.valueOf(repo.remoteUrl()), taskId));
        });
  }

  private void others(
      String project,
      ProjectConfig declared,
      List<Subject> subjects,
      Set<String> held,
      Set<String> failed) {
    String remote = declared.url();
    if (remote == null || remote.isBlank()) {
      return;
    }
    Answer<List<ReviewRequest>> awaiting = notes.awaitingReview(project, remote);
    if (awaiting.facts().isEmpty()) {
      failed.add(project);
      return;
    }
    awaiting.facts().get().stream()
        .filter(request -> held.add(request.url()))
        .forEach(request -> subjects.add(new Subject(project, request.url(), remote, "")));
  }
}
