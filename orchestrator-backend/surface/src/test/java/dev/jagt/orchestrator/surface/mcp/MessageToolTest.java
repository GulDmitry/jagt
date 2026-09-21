package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageToolTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void refusesABrokenMessageBeforeTheToolItselfRunsAtAll() {
        AtomicBoolean ran = new AtomicBoolean();
        ToolHandler tool = MessageTool.of(mapper, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> {
                    ran.set(true);
                    return "ran";
                });

        assertThatThrownBy(() -> tool.call(mapper.readTree("{\"status\":\"ALMOST_DONE\"}"), "ABC-1"))
                .hasMessageContaining("status: one of");
        assertThat(ran).isFalse();
    }

    @Test
    void namesAMissingFieldBesideEverythingElseWrongRatherThanOnItsOwn() {
        ToolHandler tool = MessageTool.of(mapper, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> "ran");

        assertThatThrownBy(() -> tool.call(mapper.readTree("{\"outcome\":\"done\"}"), "ABC-1"))
                .hasMessageContaining("status: required")
                .hasMessageContaining("outcome: one of");
    }

    @Test
    void ignoresAFieldTheMessageDoesNotDeclare() {
        ToolHandler tool = MessageTool.of(mapper, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> said.message());

        String answer = tool.call(mapper.readTree(
                "{\"status\":\"IN_PROGRESS\",\"message\":\"working\",\"mood\":\"cheerful\"}"), "ABC-1");

        assertThat(answer).isEqualTo("working");
    }

    @Test
    void readsEveryFieldTheMessageDeclaresWithoutBeingToldOneByOne() {
        ToolHandler tool = MessageTool.of(mapper, AgentStatusMessage.class,
                (said, caller) -> new MessageContext(java.util.List.of("api")),
                (said, caller) -> said.reviewRequests().toString() + " " + said.taskId());

        String answer = tool.call(mapper.readTree("{\"status\":\"CI_POLLING\",\"taskId\":\"ABC-9\","
                + "\"reviewRequests\":{\"api\":\"https://host/mr/1\"}}"), "ABC-1");

        assertThat(answer).isEqualTo("{api=https://host/mr/1} ABC-9");
    }

    @Test
    void readsAFieldLeftBlankAsOneLeftOut() {
        ToolHandler tool = MessageTool.of(mapper, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> String.valueOf(said.outcome()));

        String answer = tool.call(mapper.readTree(
                "{\"status\":\"IN_PROGRESS\",\"message\":\"working\",\"outcome\":\"\"}"), "ABC-1");

        assertThat(answer).isEqualTo("null");
    }
}
