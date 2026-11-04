package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;

/** A read that needs no tool goes to the local model where one is set for its kind, and to the cloud where not or where it failed. */
@Component
@RequiredArgsConstructor
@Slf4j
class ToolLessRead {

    private final HeadlessClaude claude;
    private final OpenAiCompatibleModel localModel;
    private final LocalModelProperties local;

    Answer<JsonNode> ask(String prompt, String schema, String label, AssistantCallKind kind, Duration timeout) {
        if (!local.serves(kind)) {
            return claude.ask(prompt, schema, label, kind, timeout);
        }
        Answer<JsonNode> answer = localModel.ask(prompt, schema, label, kind);
        if (answer.facts().isPresent()) {
            return answer;
        }
        log.atWarn().setMessage("local read fell back to cloud")
                .addKeyValue("ref", label)
                .addKeyValue("kind", kind)
                .addKeyValue("cause", answer.failure())
                .log();
        Answer<JsonNode> cloud = claude.ask(prompt, schema, label, kind, timeout);
        return new Answer<>(cloud.facts(), answer.usage().plus(cloud.usage()), cloud.failure(), cloud.refused(),
                cloud.reach());
    }
}
