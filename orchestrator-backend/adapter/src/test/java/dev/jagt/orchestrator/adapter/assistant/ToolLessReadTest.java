package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolLessReadTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private final HeadlessClaude claude = mock(HeadlessClaude.class);
    private final OpenAiCompatibleModel localModel = mock(OpenAiCompatibleModel.class);
    private final LocalModelProperties on = LocalModelProperties.off().withEndpoint("http://127.0.0.1:8080/v1")
            .withModel("qwen");
    private final Answer<tools.jackson.databind.JsonNode> localAnswer = new Answer<>(
            Optional.of(new JsonMapper().createObjectNode().put("from", "local")), TokenUsage.ofCall(10, 0, 2, 0));
    private final Answer<tools.jackson.databind.JsonNode> cloudAnswer = new Answer<>(
            Optional.of(new JsonMapper().createObjectNode().put("from", "cloud")), TokenUsage.ofCall(50, 0, 5, 0.01));

    @Test
    void asksTheLocalModelOnlyWhereTheKindIsListed() {
        when(localModel.ask("p", "{}", "command mapping", AssistantCallKind.COMMAND_MAP)).thenReturn(localAnswer);

        var answer = new ToolLessRead(claude, localModel, on).ask("p", "{}", "command mapping",
                AssistantCallKind.COMMAND_MAP, TIMEOUT);

        assertThat(answer.facts().orElseThrow().path("from").asString("")).isEqualTo("local");
        verifyNoInteractions(claude);
    }

    @Test
    void asksTheCloudOnceWhereTheLocalModelFailsAndAddsUpBothCosts() {
        when(localModel.ask("p", "{}", "command mapping", AssistantCallKind.COMMAND_MAP))
                .thenReturn(Answer.failed(TokenUsage.ofCall(10, 0, 0, 0), "http 500"));
        when(claude.ask("p", "{}", "command mapping", AssistantCallKind.COMMAND_MAP, TIMEOUT)).thenReturn(cloudAnswer);

        var answer = new ToolLessRead(claude, localModel, on).ask("p", "{}", "command mapping",
                AssistantCallKind.COMMAND_MAP, TIMEOUT);

        assertThat(answer.facts().orElseThrow().path("from").asString("")).isEqualTo("cloud");
        assertThat(answer.usage().inputTokens()).isEqualTo(60);
    }

    @Test
    void asksTheCloudWhereTheKindIsNotListed() {
        when(claude.ask(eq("p"), any(), any(), eq(AssistantCallKind.QA_CHECK), any())).thenReturn(cloudAnswer);

        var answer = new ToolLessRead(claude, localModel, on).ask("p", "{}", "qa", AssistantCallKind.QA_CHECK, TIMEOUT);

        assertThat(answer.facts().orElseThrow().path("from").asString("")).isEqualTo("cloud");
        verifyNoInteractions(localModel);
    }

    @Test
    void asksTheCloudWhereNoEndpointIsSet() {
        when(claude.ask("p", "{}", "command mapping", AssistantCallKind.COMMAND_MAP, TIMEOUT)).thenReturn(cloudAnswer);

        new ToolLessRead(claude, localModel, LocalModelProperties.off()).ask("p", "{}", "command mapping",
                AssistantCallKind.COMMAND_MAP, TIMEOUT);

        verify(claude).ask("p", "{}", "command mapping", AssistantCallKind.COMMAND_MAP, TIMEOUT);
        verifyNoInteractions(localModel);
    }
}
