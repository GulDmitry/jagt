package dev.jagt.orchestrator.codehost;

/**
 * One unresolved comment as the single line a review brief lists: {@code author (file:line): body}.
 *
 * <p>Defined once for every host: an agent addressing a round must not have to learn a second format because
 * the review happens to live somewhere else.
 */
final class RelayLine {

    /** Bot reviewers write essays; the agent needs the substance, not the whole novel. */
    private static final int MAX_CHARS = 2000;

    private RelayLine() {
    }

    static String of(String author, String file, long line, String body) {
        String where = file == null || file.isBlank() ? "" : " (" + file + (line > 0 ? ":" + line : "") + ")";
        return author + where + ": " + oneLine(body == null ? "" : body);
    }

    /** The brief lists one comment per line, so a comment's own line breaks would shred that list. */
    private static String oneLine(String body) {
        String flat = body.replaceAll("\\s*\\R\\s*", " ").strip();
        return flat.length() <= MAX_CHARS ? flat : flat.substring(0, MAX_CHARS) + " […]";
    }
}
