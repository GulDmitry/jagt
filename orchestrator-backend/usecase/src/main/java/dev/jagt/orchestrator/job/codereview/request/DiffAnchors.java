package dev.jagt.orchestrator.job.codereview.request;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the line of a file's new side a finding's anchor quotes, in a unified diff. */
public final class DiffAnchors {

  private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

  private DiffAnchors() {}

  /** The first new-side line of {@code file} holding {@code anchor}; zero where none does. */
  public static int lineOf(String diff, String file, String anchor) {
    String quoted = anchor.strip();
    if (file.isBlank() || quoted.isEmpty()) {
      return 0;
    }
    int line = 0;
    for (String text : patchOf(diff, file)) {
      Matcher hunk = HUNK.matcher(text);
      if (hunk.find()) {
        line = Integer.parseInt(hunk.group(1));
      } else if (line > 0 && onNewSide(text)) {
        if (text.substring(1).contains(quoted)) {
          return line;
        }
        line++;
      }
    }
    return 0;
  }

  private static boolean onNewSide(String text) {
    return !text.isEmpty() && !text.startsWith("-") && !text.startsWith("\\");
  }

  private static List<String> patchOf(String diff, String file) {
    List<String> patch = new ArrayList<>();
    boolean inFile = false;
    for (String text : diff.lines().toList()) {
      if (text.startsWith("+++ ")) {
        inFile = text.substring(4).strip().replaceFirst("^b/", "").equals(file);
      } else if (inFile) {
        patch.add(text);
      }
    }
    return patch;
  }
}
