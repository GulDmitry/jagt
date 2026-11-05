package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One read on a local OpenCode, booked the moment it returns. It runs outside {@code AssistantProcess}, whose breaker
 * is the cloud's: a local failure must not trip it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class HeadlessOpenCode {

    private static final JsonMapper MAPPER = new JsonMapper();

    private final Processes processes;
    private final OpenCodeProperties opencode;
    private final UsageTracker usageTracker;
    private final OpenCodeGate gate;

    HeadlessReply run(OpenCodeRead read) {
        String fence = UUID.randomUUID().toString();
        try {
            return fenced(read, fence);
        } finally {
            gate.close(fence);
        }
    }

    private HeadlessReply fenced(OpenCodeRead read, String fence) {
        String label = read.cwd().toString();
        Map<String, String> env = new HashMap<>(gate.open(fence, read.scope().fenced()));
        env.put("OPENCODE_CONFIG_CONTENT", OpenCodeConfig.json(opencode.endpoint(), read.model(), read.scope(),
                gate.directory()));
        env.put("OPENCODE_DISABLE_PROJECT_CONFIG", "1");
        Processes.Result result;
        try {
            result = processes.run(read.cwd(), read.timeout(), env,
                    List.of(opencode.command(), "run", "--standalone", "--format", "json", read.framed()));
        } catch (Processes.Stopped unjudged) {
            throw unjudged;
        } catch (RuntimeException e) {
            log.atWarn().setMessage("opencode read did not return")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", Causes.of(e))
                    .addKeyValue("limit", read.timeout())
                    .addKeyValue("effect", "token cost unmeasured")
                    .log();
            return HeadlessReply.failed(TokenUsage.NONE, "did not return: " + Causes.of(e));
        }
        OpenCodeEvents events = OpenCodeEvents.parse(result.stdout());
        usageTracker.record(read.kind(), events.usage());
        if (result.exitCode() != 0) {
            log.atWarn().setMessage("opencode read failed")
                    .addKeyValue("ref", label)
                    .addKeyValue("exit", result.exitCode())
                    .addKeyValue("cause", OneLine.clipped(result.stderr().isBlank() ? result.stdout() : result.stderr()))
                    .log();
            return HeadlessReply.failed(events.usage(), "exit " + result.exitCode());
        }
        if (!gate.heard(fence) && events.calls().stream().anyMatch(call -> "completed".equals(call.status()))) {
            log.atError().setMessage("opencode read unfenced")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", "no call reached the read gate")
                    .addKeyValue("effect", "answer discarded")
                    .log();
            return HeadlessReply.failed(events.usage(), "unfenced: no call reached the read gate");
        }
        log.atInfo().setMessage("opencode read gated")
                .addKeyValue("ref", label)
                .addKeyValue("calls", gate.heardCalls(fence))
                .addKeyValue("refused", gate.refused(fence))
                .log();
        Optional<String> violation = OpenCodeToolCalls.violation(read.scope(), events.calls());
        if (violation.isPresent()) {
            log.atError().setMessage("opencode read out of scope")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", violation.get())
                    .addKeyValue("effect", "answer discarded")
                    .log();
            return HeadlessReply.failed(events.usage(), "out of scope: " + violation.get());
        }
        return new HeadlessReply(envelope(events, read, label), events.usage(), "");
    }

    private static JsonNode envelope(OpenCodeEvents events, OpenCodeRead read, String label) {
        ObjectNode told = MAPPER.createObjectNode().put("result", events.answer().orElse(""));
        ObjectNode envelope = MAPPER.createObjectNode();
        SchemaAnswer.answerOf(told, label, read.schema()).ifPresent(answer -> envelope.set("structured_output", answer));
        return envelope;
    }
}
