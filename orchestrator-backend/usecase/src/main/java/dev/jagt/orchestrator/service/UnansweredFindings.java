package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.plan.Artifact;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Before a task holds a request, the only round relayed to its session is the pre-ship read's: its
 * findings stay open until the session writes its blocks.
 */
@Service
@RequiredArgsConstructor
public class UnansweredFindings {

  private final StateService stateService;
  private final HandBackDue handBackDue;

  /** Empty where none awaits an answer; else the sentence a ship is declined with. */
  public Optional<String> awaiting(String taskId) {
    return stateService
        .task(taskId)
        .filter(task -> !task.hasReviewRequest())
        .flatMap(handBackDue::unanswered)
        .map(round ->
            round.threads()
                + " finding(s) of the review before the ship await your block in "
                + Artifact.REPLIES.fileName()
                + " — answer each, then ship");
  }
}
