package dev.jagt.orchestrator.service.triage;

import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import java.util.List;
import lombok.With;

@With
public record TestTriageJudgementBuilder(Verdict verdict, String issue, String pattern) {

  public static TestTriageJudgementBuilder triageJudgement() {
    return new TestTriageJudgementBuilder(Verdict.READY, "none: the work moved", "unknown");
  }

  public Judgement build() {
    return new Judgement(
        "",
        verdict,
        List.of(new Finding("-", issue, pattern, "noise", "-", "scope")),
        "",
        List.of(new RoundReviewer.Premise("the trail shows one tree", "")),
        "",
        Press.NONE);
  }
}
