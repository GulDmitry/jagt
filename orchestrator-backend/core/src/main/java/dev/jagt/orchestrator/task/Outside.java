package dev.jagt.orchestrator.task;

import java.util.regex.Pattern;

/** Text written by people outside the human's control, fenced so a prompt marks it as data. */
public final class Outside {

    private static final Pattern DELIMITER = Pattern.compile("<(\\s*/?\\s*outside)", Pattern.CASE_INSENSITIVE);

    private Outside() {
    }

    /** The delimiter cannot be forged: any opening or closing of the fence inside {@code text} is escaped. */
    public static String fence(String source, String text) {
        return "<outside source=\"" + source + "\">\n" + DELIMITER.matcher(text).replaceAll("&lt;$1") + "\n</outside>";
    }
}
