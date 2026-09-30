package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.Message;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Violation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Admitting the caller, reading a tool's arguments as a message, judging it, and only then running it — in ONE
 * place, so that a tool cannot be the one that skipped a step and a test cannot exercise a path the server does
 * not take.
 */
public final class MessageTool {

    private MessageTool() {
    }

    public static <T extends Message> ToolHandler of(ObjectMapper mapper, String name, Audience audience,
                                                     Class<T> message,
                                                     BiFunction<T, String, MessageContext> context,
                                                     MessageHandler<T> handler) {
        return (args, callerTaskId) -> {
            if (!audience.admits(callerTaskId)) {
                throw new ToolRefusal(ToolFailure.PERMISSION, name + " is Master-only: a sub-agent may only act"
                        + " inside its own worktree");
            }
            T said;
            try {
                said = mapper.treeToValue(args, message);
            } catch (JacksonException unreadable) {
                throw new ToolRefusal(ToolFailure.VALIDATION, unreadable.getOriginalMessage());
            }
            List<Violation> violations = said.violations(context.apply(said, callerTaskId));
            if (!violations.isEmpty()) {
                throw new ToolRefusal(ToolFailure.VALIDATION, Message.refusal(violations));
            }
            return handler.call(said, callerTaskId);
        };
    }
}
