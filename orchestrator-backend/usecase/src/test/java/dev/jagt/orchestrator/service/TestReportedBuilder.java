package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.protocol.Reported;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.Map;
import lombok.With;

@With
public record TestReportedBuilder(String link) {

  public static TestReportedBuilder reported() {
    return new TestReportedBuilder("https://host/team/proj/-/merge_requests/9");
  }

  public Reported build() {
    return new Reported(
        TaskStatus.CI_POLLING, AgentReport.PLAIN, "shipped", link, Map.of(), false, null, Map.of());
  }
}
