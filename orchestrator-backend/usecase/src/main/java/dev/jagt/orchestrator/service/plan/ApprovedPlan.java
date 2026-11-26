package dev.jagt.orchestrator.service.plan;

import dev.jagt.orchestrator.service.repo.UnaffectedRepos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What an approved plan does to its task: the repositories it names are cut, its text is kept as
 * the baseline, the repositories it spares leave.
 */
@Component
@RequiredArgsConstructor
public class ApprovedPlan {

  private final PlanPlacement placement;
  private final PlanBaseline baseline;
  private final UnaffectedRepos unaffected;
  private final PlanVerdict verdict;

  /**
   * Whether the task had no repository and now holds the plan's; throws {@link Unplaceable} where
   * it cannot.
   */
  public boolean settle(String taskId) {
    boolean placed = placement.place(taskId);
    baseline.keep(taskId);
    return placed;
  }

  /** Whether the plan's head calls its ticket sound. */
  public boolean sound(String taskId) {
    return verdict.sound(taskId);
  }

  public void leave(String taskId) {
    unaffected.leave(taskId);
  }
}
