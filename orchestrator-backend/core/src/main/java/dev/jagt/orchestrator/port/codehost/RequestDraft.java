package dev.jagt.orchestrator.port.codehost;

/** A request jagt itself proposes: {@code branch} into {@code target}. */
public record RequestDraft(String branch, String target, String title, String description) {}
