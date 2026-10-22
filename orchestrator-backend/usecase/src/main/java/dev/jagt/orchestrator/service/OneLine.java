package dev.jagt.orchestrator.service;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Text as one table row: whitespace flattened, cut to a width. */
public final class OneLine {

    private static final int CLIP = 400;

    private OneLine() {
    }

    /** Null stays null. */
    public static @Nullable String of(@Nullable String text) {
        return text == null ? null : text.replaceAll("\\s+", " ").strip();
    }

    /** Flattened and cut to a width a log line or a notice still reads at a glance; null stays null. */
    public static @Nullable String clipped(@Nullable String text) {
        return of(text, CLIP);
    }

    /** Null stays null. */
    public static @Nullable String of(@Nullable String text, int width) {
        if (text == null) {
            return null;
        }
        String flat = Objects.requireNonNull(of(text));
        return flat.length() <= width ? flat : flat.substring(0, width - 1) + "…";
    }
}
