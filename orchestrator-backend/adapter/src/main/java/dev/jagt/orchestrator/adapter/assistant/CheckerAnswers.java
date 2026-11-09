package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.task.AcceptanceDecline;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@Slf4j
final class CheckerAnswers {

  private CheckerAnswers() {}

  static Answer<AcceptanceVerdict> verdictOf(CheckerReader.Run run, HeadlessReply reply) {
    AcceptanceVerdict verdict = verdict(run.sha(), reply);
    write(run.worktree().resolve(run.role() + "-report.md"), run.role(), verdict);
    return new Answer<>(Optional.of(verdict), reply.usage());
  }

  static Answer<AcceptanceDecline> declineOf(CheckerReader.Run run, HeadlessReply reply) {
    return new Answer<>(Optional.of(decline(run.sha(), reply)), reply.usage());
  }

  static AcceptanceDecline decline(String sha, HeadlessReply reply) {
    JsonNode answer = reply.envelope() == null ? null : reply.envelope().path("structured_output");
    return answer == null || !answer.isObject()
        ? AcceptanceDecline.failed(
            sha,
            reply.envelope() == null
                ? "the check failed: " + reply.cause()
                : "the check answered nothing readable")
        : new AcceptanceDecline(
            sha, answer.path("failure").asString(""), answer.path("dispute").asString(""));
  }

  static void write(Path file, String role, AcceptanceVerdict verdict) {
    if (verdict.rows().isEmpty()) {
      return;
    }
    try {
      Files.writeString(file, verdict.report(role));
    } catch (IOException unwritten) {
      log.atWarn()
          .setMessage("acceptance report not written")
          .addKeyValue("file", file)
          .addKeyValue("cause", unwritten.toString())
          .log();
    }
  }

  static AcceptanceVerdict verdict(String sha, HeadlessReply reply) {
    JsonNode envelope = reply.envelope();
    if (envelope == null) {
      return AcceptanceVerdict.failed(sha, "the check failed: " + reply.cause());
    }
    JsonNode answer = envelope.path("structured_output");
    if (!answer.isObject()) {
      return AcceptanceVerdict.failed(sha, "the check answered nothing readable");
    }
    List<AcceptanceVerdict.Row> rows = new ArrayList<>();
    for (JsonNode row : answer.path("rows")) {
      Optional<AcceptanceVerdict.Status> status =
          AcceptanceVerdict.Status.of(row.path("status").asString(""));
      rows.add(
          new AcceptanceVerdict.Row(
              row.path("criterion").asString(""),
              status.orElse(AcceptanceVerdict.Status.UNPROVABLE),
              row.path("evidence").asString("")));
    }
    return new AcceptanceVerdict(
        sha,
        answer.path("failure").asString(""),
        rows,
        answer.path("question").asString(""),
        answer.path("waitingOn").asString(""),
        new AcceptanceVerdict.Flows(
            namedFlows(answer.path("flows")), answer.path("environment").asString("")));
  }

  private static List<AcceptanceVerdict.Flows.Flow> namedFlows(JsonNode flows) {
    List<AcceptanceVerdict.Flows.Flow> named = new ArrayList<>();
    for (JsonNode flow : flows) {
      named.add(
          flow.isObject()
              ? new AcceptanceVerdict.Flows.Flow(
                  flow.path("id").asString(""), flow.path("criterion").asString(""))
              : new AcceptanceVerdict.Flows.Flow(flow.asString(""), ""));
    }
    return named;
  }
}
