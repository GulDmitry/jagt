package dev.jagt.orchestrator.adapter.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Joint probability of the tokens spelling the values of a schema's enum properties. */
final class EnumFieldConfidence {

  private EnumFieldConfidence() {}

  static OptionalDouble of(JsonNode schema, String content, JsonNode tokens) {
    List<int[]> spans = new ArrayList<>();
    for (var property : schema.path("properties").properties()) {
      if (property.getValue().has("enum")) {
        valueSpan(content, property.getKey()).ifPresent(spans::add);
      }
    }
    if (spans.isEmpty() || !tokens.isArray()) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(Math.exp(logprobWithin(spans, tokens)));
  }

  private static Optional<int[]> valueSpan(String content, String property) {
    Matcher match =
        Pattern.compile("\"" + Pattern.quote(property) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .matcher(content);
    return match.find() ? Optional.of(new int[] {match.start(1), match.end(1)}) : Optional.empty();
  }

  private static double logprobWithin(List<int[]> spans, JsonNode tokens) {
    double sum = 0;
    int offset = 0;
    for (JsonNode token : tokens) {
      int end = offset + token.path("token").asString("").length();
      if (overlapsAny(spans, offset, end)) {
        sum += token.path("logprob").asDouble(0);
      }
      offset = end;
    }
    return sum;
  }

  private static boolean overlapsAny(List<int[]> spans, int from, int to) {
    return spans.stream().anyMatch(span -> from < span[1] && to > span[0]);
  }
}
