package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import java.util.List;
import lombok.With;

@With
public record TestAcceptanceVerdictBuilder(List<AcceptanceVerdict.Row> rows, String question) {

  public static TestAcceptanceVerdictBuilder acceptanceVerdict() {
    return new TestAcceptanceVerdictBuilder(
        List.of(
            new AcceptanceVerdict.Row("login works", AcceptanceVerdict.Status.MET, "GET /me 200")),
        "");
  }

  public AcceptanceVerdict build() {
    return new AcceptanceVerdict(
        "proj@0123456789abcdef", "", rows, question, "", AcceptanceVerdict.Flows.none());
  }
}
