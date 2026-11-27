package dev.jagt.orchestrator.job.codereview;

import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.JobFaults;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.assistant.RoundReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The model reads of the code reviewer. A read that failed is reported as its job's failure; a
 * third-party read is charged to the request's ledger row.
 */
@Component
@RequiredArgsConstructor
public class ReviewReads {

  private final RoundReader rounds;
  private final ReviewLedger ledger;
  private final JobFaults faults;

  Judgement read(Subject subject, RoundReviewer.Round round) {
    Answer<Judgement> read = rounds.read(round);
    ledger.update(
        subject.url(),
        subject.project(),
        subject.taskId(),
        row -> row.withCostUsd(row.signals().costUsd() + read.usage().costUsd()));
    return reported(read.facts().orElse(Judgement.failed("the review answered nothing")));
  }

  Judgement reported(Judgement judged) {
    if (!judged.failure().isBlank()) {
      faults.faulted(judged.failure());
    }
    return judged;
  }
}
