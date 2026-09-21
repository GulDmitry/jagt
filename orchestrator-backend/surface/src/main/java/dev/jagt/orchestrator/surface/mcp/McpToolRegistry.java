package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.Message;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Schema;

import java.util.function.BiFunction;

/**
 * Where a tool says it exists. There is one way to declare one and its arguments ARE a protocol message: the
 * schema comes off the message, the arguments are read into it, and it is judged before the handler sees it. A
 * tool that skips validation is not something to test for — it does not compile.
 */
@FunctionalInterface
public interface McpToolRegistry {

    /** {@code context} answers what the message's rules need to know about the world. */
    <T extends Message> void tool(String name, Schema schema, Class<T> message,
                                  BiFunction<T, String, MessageContext> context, MessageHandler<T> handler);
}
