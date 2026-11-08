package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaVerdict;
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

  static Answer<QaVerdict> verdictOf(CheckerReader.Run run, HeadlessReply reply) {
    QaVerdict verdict = verdict(run.sha(), reply);
    write(run.worktree().resolve(run.role() + "-report.md"), run.role(), verdict);
    return new Answer<>(Optional.of(verdict), reply.usage());
  }

  static Answer<QaDecline> declineOf(CheckerReader.Run run, HeadlessReply reply) {
    return new Answer<>(Optional.of(decline(run.sha(), reply)), reply.usage());
  }

  static QaDecline decline(String sha, HeadlessReply reply) {
    JsonNode answer = reply.envelope() == null ? null : reply.envelope().path("structured_output");
    return answer == null || !answer.isObject()
        ? QaDecline.failed(
            sha,
            reply.envelope() == null
                ? "the check failed: " + reply.cause()
                : "the check answered nothing readable")
        : new QaDecline(
            sha, answer.path("failure").asString(""), answer.path("dispute").asString(""));
  }

  static void write(Path file, String role, QaVerdict verdict) {
    if (verdict.rows().isEmpty()) {
      return;
    }
    try {
      Files.writeString(file, verdict.report(role));
    } catch (IOException unwritten) {
      log.atWarn()
          .setMessage("qa report not written")
          .addKeyValue("file", file)
          .addKeyValue("cause", unwritten.toString())
          .log();
    }
  }

  static QaVerdict verdict(String sha, HeadlessReply reply) {
    JsonNode envelope = reply.envelope();
    if (envelope == null) {
      return QaVerdict.failed(sha, "the check failed: " + reply.cause());
    }
    JsonNode answer = envelope.path("structured_output");
    if (!answer.isObject()) {
      return QaVerdict.failed(sha, "the check answered nothing readable");
    }
    List<QaVerdict.Row> rows = new ArrayList<>();
    for (JsonNode row : answer.path("rows")) {
      Optional<QaVerdict.Status> status = QaVerdict.Status.of(row.path("status").asString(""));
      rows.add(
          new QaVerdict.Row(
              row.path("criterion").asString(""),
              status.orElse(QaVerdict.Status.UNPROVABLE),
              row.path("evidence").asString("")));
    }
    return new QaVerdict(
        sha,
        answer.path("failure").asString(""),
        rows,
        answer.path("question").asString(""),
        answer.path("waitingOn").asString(""),
        new QaVerdict.Flows(
            flowIds(answer.path("flows")), answer.path("environment").asString("")));
  }

  private static List<String> flowIds(JsonNode flows) {
    List<String> ids = new ArrayList<>();
    for (JsonNode flow : flows) {
      ids.add(flow.asString(""));
    }
    return ids;
  }
}
