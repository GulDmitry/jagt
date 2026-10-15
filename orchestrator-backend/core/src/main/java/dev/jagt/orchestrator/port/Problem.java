package dev.jagt.orchestrator.port;

/** {@code key} is what the human changes: a config key, a binary to install, or the unit to fix. */
public record Problem(String key, String text) {
}
