package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.service.OriginContext;
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
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
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
    void countsAnArgumentOfTheWrongShapeAsACallToCorrect() {
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> "ran");

        assertThatThrownBy(() -> tool.call(mapper.readTree("{\"status\":{\"is\":\"DONE\"}}"), "ABC-1"))
                .isInstanceOfSatisfying(ToolRefusal.class,
                        refusal -> assertThat(refusal.failure()).isEqualTo(ToolFailure.VALIDATION));
    }

    @Test
    void namesAMissingFieldBesideEverythingElseWrongRatherThanOnItsOwn() {
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> "ran");

        assertThatThrownBy(() -> tool.call(mapper.readTree("{\"outcome\":\"done\"}"), "ABC-1"))
                .hasMessageContaining("status: required")
                .hasMessageContaining("outcome: one of");
    }

    @Test
    void ignoresAFieldTheMessageDoesNotDeclare() {
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> said.message());

        String answer = tool.call(mapper.readTree(
                "{\"status\":\"IN_PROGRESS\",\"message\":\"working\",\"mood\":\"cheerful\",\"why\":\"started\"}"), "ABC-1");

        assertThat(answer).isEqualTo("working");
    }

    @Test
    void refusesAMisspellingOfAFieldItLeftOutRatherThanActingOnTheCallersOwnTask() {
        AtomicBoolean ran = new AtomicBoolean();
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> {
                    ran.set(true);
                    return "ran";
                });

        assertThatThrownBy(() -> tool.call(mapper.readTree(
                "{\"status\":\"IN_PROGRESS\",\"task_id\":\"ABC-9\"}"), "ABC-1"))
                .hasMessageContaining("task_id: not a field; did you mean taskId?");
        assertThat(ran).isFalse();
    }

    @Test
    void readsEveryFieldTheMessageDeclaresWithoutBeingToldOneByOne() {
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
                (said, caller) -> new MessageContext(java.util.List.of("api"), false, java.util.List.of()),
                (said, caller) -> said.reviewRequests().toString() + " " + said.taskId());

        String answer = tool.call(mapper.readTree("{\"status\":\"CI_POLLING\",\"taskId\":\"ABC-9\",\"why\":\"request opened\","
                + "\"reviewRequests\":{\"api\":\"https://host/mr/1\"}}"), "ABC-1");

        assertThat(answer).isEqualTo("{api=https://host/mr/1} ABC-9");
    }

    @Test
    void readsAFieldLeftBlankAsOneLeftOut() {
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> String.valueOf(said.outcome()));

        String answer = tool.call(mapper.readTree(
                "{\"status\":\"IN_PROGRESS\",\"message\":\"working\",\"outcome\":\"\",\"why\":\"started\"}"), "ABC-1");

        assertThat(answer).isEqualTo("null");
    }

    @Test
    void readsACallCarryingNoArgumentsAsOneWithEveryFieldLeftOut() {
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> "ran");

        assertThatThrownBy(() -> tool.call(mapper.missingNode(), "ABC-1"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("status: required");
    }

    @Test
    void refusesAReportGivingNoReasonAsACallToCorrect() {
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> "ran");

        assertThatThrownBy(() -> tool.call(mapper.readTree("{\"status\":\"IN_PROGRESS\"}"), "ABC-1"))
                .isInstanceOfSatisfying(ToolRefusal.class,
                        refusal -> assertThat(refusal.failure()).isEqualTo(ToolFailure.VALIDATION))
                .hasMessageContaining("why: required: one sentence of at most 120 characters");
    }

    @Test
    void runsTheToolWithTheReasonItWasGiven() {
        ToolHandler tool = MessageTool.of(mapper, "update_agent_status", Audience.ANYONE, AgentStatusMessage.class,
                (said, caller) -> MessageContext.NONE, (said, caller) -> OriginContext.why());

        String answer = tool.call(mapper.readTree(
                "{\"status\":\"REVIEW_PENDING\",\"why\":\"work done, tests green, ready for review\"}"), "ABC-1");

        assertThat(answer).isEqualTo("work done, tests green, ready for review");
    }
}
