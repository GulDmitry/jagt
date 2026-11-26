package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.launch.PendingStarts;
import dev.jagt.orchestrator.service.launch.TaskLaunches;
import dev.jagt.orchestrator.task.launch.LaunchRequest;
import dev.jagt.orchestrator.task.launch.Launched;
import dev.jagt.orchestrator.task.launch.PendingStart;
import dev.jagt.orchestrator.task.launch.PendingStart.Placement;
import dev.jagt.orchestrator.task.ticket.TicketFacts;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * The one place a task is started. It owns the decisions launching needs and nothing about how the
 * request arrived.
 */
@Service
@RequiredArgsConstructor
public class TaskLauncher {

  private final ConfigService configService;
  private final TaskLaunches launches;
  private final TaskResume resumes;
  private final PendingStarts pending;

  /**
   * Spins up a task for {@code ref}, an issue key or a URL to it in any tracker, or for the words a
   * line opening on a project key carries instead. Throws {@link IllegalArgumentException} when the
   * request itself is unusable. NO TASK NAMING AN ITEM IS CREATED WITHOUT THAT ITEM'S OWN FACTS: a
   * later read cannot tell an item that has no link from one that was never reached.
   */
  public Launched launchLine(String line) {
    return launch(LaunchRequest.ofLine(line, configService.load().projects().keySet()));
  }

  public Launched launch(LaunchRequest request) {
    return recorded(pending.keyFor(request), request, Placement.PLACED, () -> start(request));
  }

  /** {@code read} is the item's facts already read and paid for, as intake reads them. */
  public Launched launch(LaunchRequest request, Answer<TicketFacts> read) {
    return recorded(
        pending.keyFor(request),
        request,
        Placement.PLACED,
        () ->
            launches.ticket(
                request,
                request.project() != null ? resolveProjects(request.project()) : null,
                read));
  }

  /**
   * Runs an interrupted start again from its saved request; the record keeps its key, so the start
   * is replaced, never doubled.
   */
  public Launched resumeStart(String key) {
    PendingStart saved =
        pending
            .find(key)
            .orElseThrow(() -> new IllegalArgumentException("no interrupted start " + key));
    return recorded(
        key,
        saved.request(),
        saved.placement(),
        () ->
            saved.placement() == Placement.UNPLACED
                ? startUnplaced(saved.request())
                : start(saved.request()));
  }

  private Launched recorded(
      String key, LaunchRequest request, Placement placement, Supplier<Launched> start) {
    pending.begin(key, request, placement);
    try {
      return start.get();
    } finally {
      pending.end(key);
    }
  }

  private Launched start(LaunchRequest request) {
    return unusable(request)
        .orElseGet(() ->
            request.ref() != null
                // An unknown project is settled before the read, not after paying for one.
                ? launches.ticket(
                    request, request.project() != null ? resolveProjects(request.project()) : null)
                : launches.written(request, resolveProjects(request.project())));
  }

  /**
   * A start with no project: only a plan can come before the repository is known, and no router is
   * asked.
   */
  public Launched launchUnplaced(LaunchRequest request) {
    return recorded(
        pending.keyFor(request), request, Placement.UNPLACED, () -> startUnplaced(request));
  }

  private Launched startUnplaced(LaunchRequest request) {
    if (AgentSessions.launchMode(request.mode()) != LaunchMode.PLAN) {
      return Launched.refused(
          "error: no project and no plan first — choose a project, or start with plan first");
    }
    return unusable(request).orElseGet(() -> launches.unplaced(request));
  }

  private static Optional<Launched> unusable(LaunchRequest request) {
    if (request.ref() != null && request.ref().codePoints().anyMatch(Character::isWhitespace)) {
      return Optional.of(
          Launched.refused(
              "error: a ticket is a key or a URL; for your own words pick a project"
                  + " and say what to do"));
    }
    return request.ref() == null && (request.notes() == null || request.notes().isBlank())
        ? Optional.of(
            Launched.refused(Launched.Reason.NOTHING_TO_DO, "error: no ticket and nothing to do"))
        : Optional.empty();
  }

  public Launched resume(String reviewRequestUrl) {
    return resumes.resume(reviewRequestUrl);
  }

  /**
   * In the order given, the FIRST being where the agent's session runs; or the only project
   * configured.
   */
  public List<String> resolveProjects(@Nullable String project) {
    Set<String> keys = configService.load().projects().keySet();
    if (project != null && !project.isBlank()) {
      List<String> named =
          Arrays.stream(project.split(","))
              .map(String::strip)
              .filter(key -> !key.isEmpty())
              .distinct()
              .toList();
      List<String> unknown = named.stream().filter(key -> !keys.contains(key)).toList();
      if (!unknown.isEmpty()) {
        throw new IllegalArgumentException("unknown project " + unknown + ". Configured: " + keys);
      }
      if (named.isEmpty()) {
        throw new IllegalArgumentException("no project named. Configured: " + keys);
      }
      return named;
    }
    if (keys.size() == 1) {
      return List.of(keys.iterator().next());
    }
    throw new IllegalArgumentException("multiple projects " + keys + " — specify one");
  }
}
