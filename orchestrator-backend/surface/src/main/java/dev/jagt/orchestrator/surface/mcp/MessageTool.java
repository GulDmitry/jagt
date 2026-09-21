package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.Message;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Violation;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Reading a tool's arguments as a message, judging it, and only then running it — in ONE place, so that a tool
 * cannot be the one that skipped a step and a test cannot exercise a path the server does not take.
 */
public final class MessageTool {

    private MessageTool() {
    }

    public static <T extends Message> ToolHandler of(ObjectMapper mapper, Class<T> message,
                                                     BiFunction<T, String, MessageContext> context,
                                                     MessageHandler<T> handler) {
        return (args, callerTaskId) -> {
            T said = mapper.treeToValue(args, message);
            List<Violation> violations = said.violations(context.apply(said, callerTaskId));
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException(Message.refusal(violations));
            }
            return handler.call(said, callerTaskId);
        };
    }
}
