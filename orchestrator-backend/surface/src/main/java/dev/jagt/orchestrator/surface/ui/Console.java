package dev.jagt.orchestrator.surface.ui;

/** The log goes to a file, so a human running the jar sees only what is written here. */
public final class Console {

  private Console() {}

  public static void say(String line) {
    System.out.println(line); // the log is a file, so stdout is the only human-visible channel
  }

  public static void problem(String line) {
    System.err.println(line); // problems go to stderr so they survive a redirected stdout
  }
}
