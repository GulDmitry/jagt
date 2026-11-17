package dev.jagt.orchestrator.service.session;

/** Where a window lives: the session holding it and the name it answers to there. */
public record SessionWindow(String session, String name) {}
