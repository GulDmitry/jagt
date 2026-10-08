package dev.jagt.orchestrator.service;

/** Text as one table row: whitespace flattened, cut to a width. */
public final class OneLine {

    private OneLine() {
    }

    /** Null stays null. */
    public static String of(String text, int width) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= width ? flat : flat.substring(0, width - 1) + "…";
    }
}
