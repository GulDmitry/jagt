package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.Message;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Schema;

import java.util.function.BiFunction;

public interface McpToolRegistry {

    /** A tool whose arguments are read by hand. Every new tool should take a message instead. */
    void tool(String name, String schemaJson, ToolHandler handler);

    /**
     * A tool whose arguments ARE a protocol message: the schema comes off the message, the arguments are read
     * into it, and it is judged before the handler sees it. A tool declared this way cannot be the one that
     * forgot to validate. {@code context} answers what the rules need to know about the world.
     */
    <T extends Message> void tool(String name, Schema schema, Class<T> message,
                                  BiFunction<T, String, MessageContext> context, MessageHandler<T> handler);
}
