package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A JSON reporter document read per flow. A flow is a spec whose tag, or whitespace-separated title
 * word, equals it (a leading {@code @} aside), or whose whole title does. Any unexpected test fails
 * the flow; a test that passed only on retry, or was skipped, leaves it unprovable.
 */
final class PlaywrightReport {

  private static final JsonMapper MAPPER = new JsonMapper();
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final JsonNode document;

  PlaywrightReport(String json) {
    this.document = MAPPER.readTree(json);
  }

  boolean isReport() {
    return document.has("suites");
  }

  List<FlowResult> results(List<String> flows, Path report) {
    return flows.stream().map(flow -> result(flow, report)).toList();
  }

  private FlowResult result(String flow, Path report) {
    List<JsonNode> specs = new ArrayList<>();
    collect(document.path("suites"), flow, specs);
    FlowResult base =
        FlowResult.defaults().withFlow(flow).withEvidence(List.of(report));
    if (specs.isEmpty()) {
      return base.withReason("no spec carries this flow");
    }
    List<String> failed = new ArrayList<>();
    List<String> unstable = new ArrayList<>();
    List<Path> evidence = new ArrayList<>(List.of(report));
    for (JsonNode spec : specs) {
      String title = spec.path("title").asString("");
      for (JsonNode test : spec.path("tests")) {
        switch (test.path("status").asString("")) {
          case "expected" -> {}
          case "unexpected" -> failed.add(title);
          default -> unstable.add(title);
        }
        attachments(test, evidence);
      }
    }
    FlowResult found = base.withEvidence(evidence);
    if (!failed.isEmpty()) {
      return found.withVerdict(Verdict.FAILED).withReason("failed: " + String.join("; ", failed));
    }
    if (!unstable.isEmpty()) {
      return found.withReason("flaky or skipped: " + String.join("; ", unstable));
    }
    return found.withVerdict(Verdict.PASSED);
  }

  private static void attachments(JsonNode test, List<Path> into) {
    for (JsonNode result : test.path("results")) {
      for (JsonNode attachment : result.path("attachments")) {
        String path = attachment.path("path").asString("");
        if (!path.isEmpty()) {
          into.add(Path.of(path));
        }
      }
    }
  }

  private static void collect(JsonNode suites, String flow, List<JsonNode> into) {
    for (JsonNode suite : suites) {
      for (JsonNode spec : suite.path("specs")) {
        if (carries(spec, flow)) {
          into.add(spec);
        }
      }
      collect(suite.path("suites"), flow, into);
    }
  }

  private static boolean carries(JsonNode spec, String flow) {
    String wanted = withoutAt(flow);
    for (JsonNode tag : spec.path("tags")) {
      if (withoutAt(tag.asString("")).equals(wanted)) {
        return true;
      }
    }
    String title = spec.path("title").asString("");
    return title.equals(flow)
        || WHITESPACE.splitAsStream(title).anyMatch(word -> withoutAt(word).equals(wanted));
  }

  private static String withoutAt(String word) {
    return word.startsWith("@") ? word.substring(1) : word;
  }
}
