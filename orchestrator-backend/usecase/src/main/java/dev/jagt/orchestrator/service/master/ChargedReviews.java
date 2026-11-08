package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.SessionReach;
import dev.jagt.orchestrator.service.TrustedGitState;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One Master read, its spend charged to the task it read for, made only while its git is trusted,
 * reaching what the session may write.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargedReviews {

  private final RoundReviewer reviewer;
  private final UsageTracker usage;
  private final TrustedGitState trusted;
  private final SessionReach reach;

  public Judgement review(String taskId, RoundReviewer.Round round) {
    Optional<String> distrusted = trusted.distrust(taskId);
    if (distrusted.isPresent()) {
      return Judgement.failed(distrusted.get());
    }
    var read = reviewer.review(round.reaching(reach.beside()));
    usage.chargeTask(taskId, read.usage());
    log.atInfo()
        .setMessage("master call charged")
        .addKeyValue("task", taskId)
        .addKeyValue("kind", AssistantCallKind.MASTER_REVIEW)
        .log();
    return read.facts().orElse(Judgement.failed("the review answered nothing"));
  }

  public boolean loadsMcpServer() {
    return reviewer.loadsMcpServer();
  }
}
