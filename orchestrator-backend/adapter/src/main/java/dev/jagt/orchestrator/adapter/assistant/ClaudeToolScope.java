package dev.jagt.orchestrator.adapter.assistant;

import java.util.ArrayList;
import java.util.List;

/**
 * The Claude Code tool list and permission rules for a read scope: reads always on, writes always
 * refused.
 */
record ClaudeToolScope(String tools, List<String> allow, List<String> refuse) {

  static ClaudeToolScope of(
      List<String> shell, List<String> refuseShell, List<String> refuseRead, boolean fetch) {
    List<String> tools = new ArrayList<>(List.of("Read", "Grep", "Glob"));
    List<String> allow = new ArrayList<>(tools);
    if (!shell.isEmpty()) {
      tools.add("Bash");
    }
    if (fetch) {
      tools.add("WebFetch");
      allow.add("WebFetch");
    }
    shell.forEach(glob -> allow.add(bash(glob)));
    List<String> refuse = new ArrayList<>(List.of("Write", "Edit", "NotebookEdit"));
    refuseShell.forEach(glob -> refuse.add(bash(glob)));
    refuseRead.forEach(glob -> refuse.add("Read(" + glob + ")"));
    return new ClaudeToolScope(String.join(",", tools), allow, refuse);
  }

  private static String bash(String glob) {
    boolean prefix = glob.indexOf('*') == glob.length() - 1;
    return "Bash(" + (prefix ? glob.substring(0, glob.length() - 1) + ":*" : glob) + ")";
  }
}
