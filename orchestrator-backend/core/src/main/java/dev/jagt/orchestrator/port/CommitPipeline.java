package dev.jagt.orchestrator.port;

/** What the host says about the checks of one commit, in its own word, {@code none} where it ran none; {@code url} is blank then. */
public record CommitPipeline(String status, String url) {
}
