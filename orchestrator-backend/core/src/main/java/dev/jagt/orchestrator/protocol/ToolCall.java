package dev.jagt.orchestrator.protocol;

import org.jspecify.annotations.Nullable;

/**
 * A session's tool call before it runs: what the tool is called, and the shell line or file it
 * names, if any.
 */
public record ToolCall(
    @Nullable String toolName, @Nullable String command, @Nullable String file) {}
