package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.service.task.Verdicts;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewVerdicts implements Verdicts {

  private final MasterReview reviews;

  @Override
  public Optional<String> said(TaskState task) {
    return reviews.of(task).map(MasterReview.Verdict::said);
  }
}
