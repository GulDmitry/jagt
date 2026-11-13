package dev.jagt.orchestrator.port;

import org.jspecify.annotations.Nullable;

/** One point told to one channel: its id, the task it is about, and the title and text for it. */
public record ChannelMessage(
    String point, @Nullable String taskId, @Nullable String alias, String title, String text) {}
