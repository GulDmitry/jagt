package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.eval.CodeReviewCase.Expected;
import dev.jagt.orchestrator.eval.CodeReviewCase.Finding;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public record CodeReviewAnswer(List<Seen> findings) {

  public record Seen(String file, String issue, String pattern, String severity, String kind) {

    String words() {
      return issue + " " + pattern + " " + kind;
    }
  }

  public static CodeReviewAnswer parse(String text) {
    int open = text.indexOf('{');
    int close = text.lastIndexOf('}');
    if (open < 0 || close < open) {
      throw EvalStalled.because("no JSON object in the answer: " + text);
    }
    Map<String, Object> root = Yamls.load(text.substring(open, close + 1));
    return new CodeReviewAnswer(
        Yamls.listOrEmpty(root.get("findings")).stream()
            .map(Yamls::map)
            .map(CodeReviewAnswer::seen)
            .toList());
  }

  private static Seen seen(Map<String, Object> row) {
    return new Seen(
        Yamls.text(row.get("file")),
        Yamls.text(row.get("issue")),
        Yamls.text(row.get("pattern")),
        Yamls.text(row.get("severity")).toLowerCase(Locale.ROOT),
        Yamls.text(row.get("kind")));
  }

  public List<String> problems(Expected expected) {
    return Stream.concat(stopping(expected), unmatched(expected)).toList();
  }

  private Stream<String> stopping(Expected expected) {
    if (!expected.clean()) {
      return Stream.empty();
    }
    return findings.stream()
        .filter(seen -> !seen.severity().equals("noise"))
        .map(seen -> "a clean change was stopped by " + seen);
  }

  private Stream<String> unmatched(Expected expected) {
    return expected.findings().stream()
        .filter(wanted -> findings.stream().noneMatch(seen -> matches(wanted, seen)))
        .map(wanted ->
            "no finding of "
                + wanted.severities()
                + " names "
                + wanted.file()
                + " with "
                + wanted.pattern()
                + ", saw "
                + findings);
  }

  private static boolean matches(Finding wanted, Seen seen) {
    return seen.file().endsWith(wanted.file())
        && wanted.pattern().matcher(seen.words()).find()
        && (wanted.severities().isEmpty() || wanted.severities().contains(seen.severity()));
  }
}
