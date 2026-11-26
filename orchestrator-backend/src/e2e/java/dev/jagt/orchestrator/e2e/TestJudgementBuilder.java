package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import java.util.List;
import lombok.With;

@With
public record TestJudgementBuilder(Verdict verdict, List<Finding> findings, String question) {

  public static TestJudgementBuilder judgement() {
    return new TestJudgementBuilder(Verdict.READY, List.of(), "");
  }

  public Judgement build() {
    return new Judgement("", verdict, findings, question, List.of(), "", Press.NONE);
  }
}
