package dev.jagt.orchestrator.task;

import java.util.regex.Pattern;

public final class Ansi {

    private static final Pattern ESCAPES = Pattern.compile(
            "\u001B\\[[0-?]*[ -/]*[@-~]|\u001B[\\]P][^\u0007\u001B]*(\u0007|\u001B\\\\)|\u001B[()][0-9A-B]|\u001B[78=>]");

    private Ansi() {
    }

    public static String strip(String screen) {
        return ESCAPES.matcher(screen).replaceAll("");
    }
}
