package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.Message;

/** What a tool does once its arguments have been read as a message and judged. */
@FunctionalInterface
public interface MessageHandler<T extends Message> {
    String call(T message, String callerTaskId);
}
