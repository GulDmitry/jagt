package dev.jagt.orchestrator.job.codereview;

import dev.jagt.orchestrator.job.codereview.request.NotePoster;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The findings of a task the review before its ship already raised, kept with the task: a finding
 * is the same one by its file, anchor and kind, and is raised once.
 */
@Component
@RequiredArgsConstructor
public class PreShipCoverage {

  private static final String SEPARATOR = ",";

  private final StateService stateService;

  /** {@code judged} without the findings already raised for {@code taskId}. */
  public Judgement unseen(String taskId, Judgement judged) {
    Set<String> raised = raised(taskId);
    return new Judgement(
        judged.failure(),
        judged.verdict(),
        judged.findings().stream().filter(finding -> !raised.contains(key(finding))).toList(),
        judged.question(),
        judged.premises(),
        judged.newTask(),
        judged.press());
  }

  public void raise(String taskId, List<Finding> findings) {
    Set<String> raised = new LinkedHashSet<>(raised(taskId));
    findings.stream().map(PreShipCoverage::key).forEach(raised::add);
    stateService.reduce(
        taskId,
        (reducer, task) ->
            reducer.told(task, ToldOnce.Kind.PRE_SHIP_COVERED, String.join(SEPARATOR, raised)));
  }

  private Set<String> raised(String taskId) {
    return stateService
        .task(taskId)
        .map(task -> task.round().toldOrNone().valueOf(ToldOnce.Kind.PRE_SHIP_COVERED))
        .filter(joined -> !joined.isBlank())
        .map(joined -> Set.copyOf(Arrays.asList(joined.split(SEPARATOR))))
        .orElse(Set.of());
  }

  static String key(Finding finding) {
    return NotePoster.digest(finding.file(), finding.anchor(), finding.kind());
  }
}
