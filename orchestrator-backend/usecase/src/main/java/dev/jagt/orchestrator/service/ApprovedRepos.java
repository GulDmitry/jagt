package dev.jagt.orchestrator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What an approved plan does to the repositories of its task: the ones it names are cut, the ones
 * it spares leave.
 */
@Component
@RequiredArgsConstructor
public class ApprovedRepos {

  private final PlanPlacement placement;
  private final UnaffectedRepos unaffected;

  /**
   * Whether the task had no repository and now holds the plan's; throws {@link Unplaceable} where
   * it cannot.
   */
  public boolean place(String taskId) {
    return placement.place(taskId);
  }

  public void leave(String taskId) {
    unaffected.leave(taskId);
  }
}
