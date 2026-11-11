package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import java.util.List;
import lombok.With;

@With
public record TestJudgementBuilder(String verdict, List<Finding> findings, String question) {

  public static TestJudgementBuilder judgement() {
    return new TestJudgementBuilder("ready", List.of(), "");
  }

  public Judgement build() {
    return new Judgement("", verdict, findings, question, List.of(), "");
  }
}
