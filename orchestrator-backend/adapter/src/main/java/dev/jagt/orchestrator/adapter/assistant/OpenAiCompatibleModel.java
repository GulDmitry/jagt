package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * One chat completion against an OpenAI-compatible endpoint, the answer held to the caller's schema
 * by the server.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class OpenAiCompatibleModel {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final int MAX_TOKENS = 400;

  private final LocalModelProperties local;
  private final UsageTracker usageTracker;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
  private final JsonMapper mapper = new JsonMapper();

  Answer<JsonNode> ask(String prompt, String schema, String label, AssistantCallKind kind) {
    TokenUsage usage = TokenUsage.NONE;
    try {
      HttpResponse<String> response =
          http.send(request(prompt, schema), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        return failed(usage, label, kind, "http " + response.statusCode());
      }
      JsonNode body = mapper.readTree(response.body());
      usage = usageOf(body);
      usageTracker.record(kind, usage);
      String content = body.path("choices").path(0).path("message").path("content").asString("");
      JsonNode answer = mapper.readTree(content);
      if (!answer.isObject()) {
        return failed(usage, label, kind, "content is not an object");
      }
      OptionalDouble confidence = confidenceOf(body, schema, content);
      Optional<String> doubt = doubtOf(confidence);
      if (doubt.isPresent()) {
        return failed(usage, label, kind, doubt.get());
      }
      confidence.ifPresent(value -> logConfidence(label, kind, value));
      return new Answer<>(Optional.of(answer), usage);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failed(usage, label, kind, "interrupted");
    } catch (IOException | RuntimeException e) {
      return failed(usage, label, kind, e.getClass().getSimpleName() + " " + e.getMessage());
    }
  }

  private HttpRequest request(String prompt, String schema) {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", local.model());
    body.put("temperature", 0);
    body.put("max_tokens", MAX_TOKENS);
    body.put("logprobs", true);
    body.putArray("messages").addObject().put("role", "user").put("content", prompt);
    body.putObject("response_format")
        .put("type", "json_schema")
        .putObject("json_schema")
        .put("name", "answer")
        .set("schema", mapper.readTree(schema));
    return HttpRequest.newBuilder(
            URI.create(local.endpoint().replaceAll("/+$", "") + "/chat/completions"))
        .timeout(local.timeout())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();
  }

  private OptionalDouble confidenceOf(JsonNode body, String schema, String content) {
    JsonNode tokens = body.path("choices").path(0).path("logprobs").path("content");
    return EnumFieldConfidence.of(mapper.readTree(schema), content, tokens);
  }

  private Optional<String> doubtOf(OptionalDouble confidence) {
    double threshold = local.minConfidence();
    if (threshold <= 0) {
      return Optional.empty();
    }
    if (confidence.isEmpty()) {
      return Optional.of("no enum-field logprobs in the response, confidence unknown");
    }
    return confidence.getAsDouble() < threshold
        ? Optional.of("confidence %.3f below %s".formatted(confidence.getAsDouble(), threshold))
        : Optional.empty();
  }

  private void logConfidence(String label, AssistantCallKind kind, double confidence) {
    log.atDebug()
        .setMessage("local model answered")
        .addKeyValue("ref", label)
        .addKeyValue("kind", kind)
        .addKeyValue("confidence", confidence)
        .log();
  }

  private static TokenUsage usageOf(JsonNode body) {
    JsonNode usage = body.path("usage");
    return TokenUsage.ofCall(
        usage.path("prompt_tokens").asLong(0), 0, usage.path("completion_tokens").asLong(0), 0);
  }

  private Answer<JsonNode> failed(
      TokenUsage usage, String label, AssistantCallKind kind, String cause) {
    String oneLine = Objects.requireNonNullElse(OneLine.clipped(cause.replace('"', '\'')), "");
    log.atWarn()
        .setMessage("local model call failed")
        .addKeyValue("ref", label)
        .addKeyValue("kind", kind)
        .addKeyValue("cause", oneLine)
        .addKeyValue("limit", local.timeout())
        .log();
    return Answer.failed(usage, oneLine);
  }
}
