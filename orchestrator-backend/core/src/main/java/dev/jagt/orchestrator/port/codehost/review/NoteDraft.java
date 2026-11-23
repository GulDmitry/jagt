package dev.jagt.orchestrator.port.codehost.review;

/**
 * A note to post: its text and the id of the note it answers, blank for a note on the request
 * itself.
 */
public record NoteDraft(String body, String replyTo) {}
