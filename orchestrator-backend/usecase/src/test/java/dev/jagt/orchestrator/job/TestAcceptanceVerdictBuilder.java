package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.AcceptanceVerdict;
import java.util.List;
import lombok.With;

@With
public record TestAcceptanceVerdictBuilder(AcceptanceVerdict.Row row) {

  public static TestAcceptanceVerdictBuilder acceptanceVerdict() {
    return new TestAcceptanceVerdictBuilder(
        new AcceptanceVerdict.Row("login works", AcceptanceVerdict.Status.MET, "GET /me 200"));
  }

  public AcceptanceVerdict build() {
    return new AcceptanceVerdict(
        "proj@0123456789abcdef", "", List.of(row), "", "", AcceptanceVerdict.Flows.none());
  }
}
