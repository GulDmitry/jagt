package dev.jagt.orchestrator.surface.ui;

/** The log goes to a file, so a human running the jar sees only what is written here. */
public final class Console {

    private Console() {
    }

    public static void say(String line) {
        System.out.println(line);
    }

    public static void problem(String line) {
        System.err.println(line);
    }
}
