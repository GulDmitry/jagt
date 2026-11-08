package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import java.util.List;

final class Unrun {

  private Unrun() {}

  static List<FlowResult> all(List<String> flows, String reason) {
    return flows.stream()
        .map(
            flow ->
                FlowResult.defaults()
                    .withFlow(flow)
                    .withVerdict(Verdict.UNPROVABLE)
                    .withReason(reason))
        .toList();
  }
}
