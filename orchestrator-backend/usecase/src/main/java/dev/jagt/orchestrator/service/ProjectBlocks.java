package dev.jagt.orchestrator.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * One block per project under {@code orchestrator.projects}, edited as lines: every line outside
 * the block stays.
 */
final class ProjectBlocks {

  private static final Pattern ROOT = Pattern.compile("(?m)^orchestrator:[ \\t]*(#.*)?$");
  private static final Pattern LEADING = Pattern.compile("^( *)\\S");
  private static final Set<String> MANAGED =
      Set.of(
          "path", "baseBranch", "deployBranch", "verifyCommand", "about", "labels", "humanPaths");
  private static final JsonMapper JSON = new JsonMapper();

  private ProjectBlocks() {}

  private record Located(
      String head, List<String> lines, int projectsAt, String parent, String entryIndent) {}

  private record Span(int from, int to) {}

  /**
   * The file with the entry first under the {@code projects:} that is a direct child of {@code
   * orchestrator:}.
   */
  static String inserted(String file, ProjectSetup.Entry entry, Path path, boolean keptLocally) {
    Located at = locate(file);
    String[] lines = at.lines().toArray(String[]::new);
    String step = at.entryIndent().substring(at.parent().length());
    lines[at.projectsAt()] =
        at.parent()
            + "projects:\n"
            + (at.entryIndent()
                    + entry.key()
                    + ":\n"
                    + managed(at.entryIndent() + step, entry, path)
                    + (keptLocally ? at.entryIndent() + step + "remote: \"none\"\n" : ""))
                .stripTrailing();
    return at.head() + String.join("\n", lines);
  }

  /**
   * The file with the managed keys of the project's block rewritten; its other keys and comments
   * stay.
   */
  static String replaced(String file, ProjectSetup.Entry entry, Path path) {
    Located at = locate(file);
    Span span = span(at, entry.key());
    String[] lines = at.lines().toArray(String[]::new);
    if (!lines[span.from()].strip().matches(Pattern.quote(entry.key()) + ":\\s*(#.*)?")) {
      throw new IllegalArgumentException(
          entry.key() + " is written inline in jagt.yml: change it by hand");
    }
    String step = at.entryIndent().substring(at.parent().length());
    String child = childIndent(lines, span, at.entryIndent() + step);
    List<String> result = new ArrayList<>(List.of(lines).subList(0, span.from() + 1));
    result.addAll(List.of(managed(child, entry, path).split("\n")));
    result.addAll(kept(lines, span, child));
    result.addAll(List.of(lines).subList(span.to(), lines.length));
    return at.head() + String.join("\n", result);
  }

  /** The file without the project's block. */
  static String removed(String file, String key) {
    Located at = locate(file);
    Span span = span(at, key);
    List<String> result = new ArrayList<>(at.lines().subList(0, span.from()));
    result.addAll(at.lines().subList(span.to(), at.lines().size()));
    return at.head() + String.join("\n", result);
  }

  private static Located locate(String file) {
    Matcher root = ROOT.matcher(file);
    if (!root.find()) {
      throw new IllegalArgumentException("jagt.yml holds no orchestrator: section");
    }
    String[] lines = file.substring(root.end()).split("\n", -1);
    String child = null;
    for (int i = 1; i < lines.length; i++) {
      String indent = indent(lines[i]);
      if (indent == null) continue;
      if (indent.isEmpty()) break;
      if (child == null) child = indent;
      if (indent.equals(child) && lines[i].strip().matches("projects:\\s*(\\{\\s*})?\\s*(#.*)?")) {
        return new Located(
            file.substring(0, root.end()), List.of(lines), i, child, entryIndent(lines, i, child));
      }
    }
    throw new IllegalArgumentException("jagt.yml holds no projects: under orchestrator: to add to");
  }

  private static String entryIndent(String[] lines, int at, String parent) {
    for (int i = at + 1; i < lines.length; i++) {
      String indent = indent(lines[i]);
      if (indent == null) continue;
      return indent.length() > parent.length() ? indent : parent + parent;
    }
    return parent + parent;
  }

  /**
   * The lines of the project's block, where comment and blank lines before the next key belong to
   * that key.
   */
  private static Span span(Located at, String key) {
    String[] lines = at.lines().toArray(String[]::new);
    int from = -1;
    int end = lines.length;
    for (int i = at.projectsAt() + 1; i < lines.length && end == lines.length; i++) {
      String indent = indent(lines[i]);
      if (indent == null) continue;
      if (endsBlock(at, from >= 0, indent)) {
        end = i;
      } else if (from < 0 && startsBlock(at, lines[i], indent, key)) {
        from = i;
      }
    }
    if (from < 0) {
      throw new IllegalArgumentException(
          "jagt.yml holds no block for the project " + key + " to change");
    }
    while (end > from + 1 && indent(lines[end - 1]) == null) {
      end--;
    }
    return new Span(from, end);
  }

  private static boolean endsBlock(Located at, boolean inBlock, String indent) {
    return indent.length() <= at.parent().length()
        || (inBlock && indent.length() <= at.entryIndent().length());
  }

  private static boolean startsBlock(Located at, String line, String indent, String key) {
    return indent.equals(at.entryIndent()) && line.strip().startsWith(key + ":");
  }

  private static String childIndent(String[] lines, Span span, String fallback) {
    for (int i = span.from() + 1; i < span.to(); i++) {
      String indent = indent(lines[i]);
      if (indent != null) return indent;
    }
    return fallback;
  }

  /**
   * The block's lines the form does not edit, with a managed key's own lines and its continuation
   * dropped.
   */
  private static List<String> kept(String[] lines, Span span, String child) {
    List<String> kept = new ArrayList<>();
    boolean dropping = false;
    for (int i = span.from() + 1; i < span.to(); i++) {
      String indent = indent(lines[i]);
      boolean continuation =
          indent != null
              && (indent.length() > child.length()
                  || (indent.equals(child) && lines[i].strip().startsWith("- ")));
      if (dropping && continuation) continue;
      dropping = indent != null && indent.equals(child) && MANAGED.contains(keyOf(lines[i]));
      if (!dropping) kept.add(lines[i]);
    }
    return kept;
  }

  private static String keyOf(String line) {
    int colon = line.indexOf(':');
    return colon < 0 ? "" : line.substring(0, colon).strip();
  }

  /** The leading spaces of a line that holds a key, or null for a blank or comment line. */
  private static @Nullable String indent(String line) {
    Matcher leading = LEADING.matcher(line);
    return leading.find() && !line.strip().startsWith("#") ? leading.group(1) : null;
  }

  private static String managed(String inner, ProjectSetup.Entry entry, Path path) {
    StringBuilder block = new StringBuilder();
    block
        .append(inner)
        .append("path: ")
        .append(JSON.writeValueAsString(path.toString()))
        .append('\n');
    block
        .append(inner)
        .append("baseBranch: ")
        .append(JSON.writeValueAsString(entry.baseBranch()))
        .append('\n');
    if (entry.deployBranch() != null && !entry.deployBranch().isBlank()) {
      block
          .append(inner)
          .append("deployBranch: ")
          .append(JSON.writeValueAsString(entry.deployBranch()))
          .append('\n');
    }
    if (entry.verifyCommand() != null && !entry.verifyCommand().isEmpty()) {
      block
          .append(inner)
          .append("verifyCommand: ")
          .append(JSON.writeValueAsString(entry.verifyCommand()))
          .append('\n');
    }
    if (entry.about() != null && !entry.about().isBlank()) {
      block
          .append(inner)
          .append("about: ")
          .append(JSON.writeValueAsString(entry.about()))
          .append('\n');
    }
    if (entry.labels() != null && !entry.labels().isEmpty()) {
      block
          .append(inner)
          .append("labels: ")
          .append(JSON.writeValueAsString(entry.labels()))
          .append('\n');
    }
    if (entry.humanPaths() != null && !entry.humanPaths().isEmpty()) {
      block
          .append(inner)
          .append("humanPaths: ")
          .append(JSON.writeValueAsString(entry.humanPaths()))
          .append('\n');
    }
    return block.toString();
  }
}
