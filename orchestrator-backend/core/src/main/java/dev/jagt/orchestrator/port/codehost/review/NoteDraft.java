package dev.jagt.orchestrator.port.codehost.review;

/**
 * A note to post: its text, the id of the note it answers (blank for a note on the request itself)
 * and the line it sits on.
 */
public record NoteDraft(String body, String replyTo, NoteAnchor anchor) {}
