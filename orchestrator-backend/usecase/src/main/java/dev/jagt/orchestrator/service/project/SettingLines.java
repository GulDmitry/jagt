package dev.jagt.orchestrator.service.project;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * A key of the configuration file edited as lines: only the key's own line changes, so every
 * comment, blank line, alignment and the order of every other key stay.
 */
final class SettingLines {

  private static final Pattern KEY_LINE =
      Pattern.compile("^( *)([A-Za-z0-9_.-]+):(?:([ \\t]+)(.*))?$");
  private static final String STEP = "  ";
  private static final JsonMapper JSON = new JsonMapper();

  private record Region(int from, int to, int parent) {}

  private record Parts(String indent, String key, String gap, String value, String comment) {}

  private final List<String> lines;

  private SettingLines(String file) {
    lines = new ArrayList<>(List.of(file.split("\n", -1)));
  }

  /**
   * The file with the value at {@code path} set; a missing key and its missing sections are written
   * at the end of the deepest section that exists.
   */
  static String changed(String file, List<String> path, Object value) {
    SettingLines at = new SettingLines(file);
    Region region = at.root();
    for (int i = 0; i < path.size(); i++) {
      int found = at.find(region, path.get(i));
      if (found < 0) {
        at.insert(region, path.subList(i, path.size()), value);
        break;
      }
      if (i == path.size() - 1) {
        at.replace(found, value);
      } else {
        region = at.inner(found);
      }
    }
    return String.join("\n", at.lines);
  }

  /** The file with every inline mapping on the way to {@code path} written as a block. */
  static String blocked(String file, List<String> path) {
    SettingLines at = new SettingLines(file);
    Region region = at.root();
    for (String segment : path) {
      int found = at.find(region, segment);
      if (found < 0) {
        break;
      }
      String value = at.parts(found).value();
      if (value.startsWith("{") && !value.replace(" ", "").equals("{}")) {
        at.expand(found);
      } else if (!value.isEmpty()) {
        break;
      }
      region = at.region(found);
    }
    return String.join("\n", at.lines);
  }

  private Region root() {
    return new Region(0, trimmed(0, lines.size()), -1);
  }

  private Region inner(int at) {
    String value = parts(at).value();
    if (value.startsWith("{")) {
      expand(at);
    } else if (!value.isEmpty()) {
      throw new IllegalArgumentException(
          parts(at).key() + " holds the value " + value + ", not a section");
    }
    return region(at);
  }

  private Region region(int at) {
    return new Region(at + 1, trimmed(at + 1, end(at)), indent(lines.get(at)).length());
  }

  private int find(Region region, String key) {
    String child = null;
    for (int i = region.from(); i < region.to(); i++) {
      Matcher line = KEY_LINE.matcher(lines.get(i));
      if (!line.matches()) {
        continue;
      }
      child = child == null ? line.group(1) : child;
      if (line.group(1).equals(child) && line.group(2).equals(key)) {
        return i;
      }
    }
    return -1;
  }

  private void insert(Region region, List<String> chain, Object value) {
    String indent = childIndent(region);
    List<String> written = new ArrayList<>();
    for (int i = 0; i < chain.size() - 1; i++) {
      written.add(indent + chain.get(i) + ":");
      indent += STEP;
    }
    written.add(indent + chain.get(chain.size() - 1) + ": " + JSON.writeValueAsString(value));
    lines.addAll(region.to(), written);
  }

  private String childIndent(Region region) {
    for (int i = region.from(); i < region.to(); i++) {
      if (!isNote(lines.get(i))) {
        return indent(lines.get(i));
      }
    }
    return region.parent() < 0 ? "" : " ".repeat(region.parent()) + STEP;
  }

  private void replace(int at, Object value) {
    Parts parts = parts(at);
    if (parts.value().isEmpty() && trimmed(at + 1, end(at)) > at + 1) {
      throw new IllegalArgumentException(parts.key() + " holds a section, not a value");
    }
    String gap = parts.gap().isEmpty() ? " " : parts.gap();
    String head = parts.indent() + parts.key() + ":" + gap + JSON.writeValueAsString(value);
    if (parts.comment().isEmpty()) {
      lines.set(at, head);
      return;
    }
    int column = lines.get(at).length() - parts.comment().length();
    lines.set(at, head + " ".repeat(Math.max(1, column - head.length())) + parts.comment());
  }

  private void expand(int at) {
    Parts parts = parts(at);
    List<String> written = new ArrayList<>();
    written.add(
        parts.indent()
            + parts.key()
            + ":"
            + (parts.comment().isEmpty() ? "" : "  " + parts.comment()));
    block(parts.value(), parts.indent() + STEP, written);
    lines.remove(at);
    lines.addAll(at, written);
  }

  private static void block(String mapping, String indent, List<String> into) {
    for (String item : items(mapping.substring(1, mapping.length() - 1))) {
      int colon = item.indexOf(':');
      if (colon < 0) {
        throw new IllegalArgumentException("An inline entry cannot be read: " + item.strip());
      }
      String key = item.substring(0, colon).strip().replaceAll("^[\"']|[\"']$", "");
      String value = item.substring(colon + 1).strip();
      if (value.startsWith("{") && !value.replace(" ", "").equals("{}")) {
        into.add(indent + key + ":");
        block(value, indent + STEP, into);
      } else {
        into.add(indent + key + ":" + (value.isEmpty() ? "" : " " + value));
      }
    }
  }

  private static List<String> items(String body) {
    boolean[] inside = quoted(body);
    List<String> items = new ArrayList<>();
    int start = 0;
    int depth = 0;
    for (int i = 0; i < body.length(); i++) {
      if (inside[i]) {
        continue;
      }
      char c = body.charAt(i);
      depth += delta(c);
      if (c == ',' && depth == 0) {
        items.add(body.substring(start, i));
        start = i + 1;
      }
    }
    if (!body.substring(start).isBlank()) {
      items.add(body.substring(start));
    }
    return items;
  }

  private static int delta(char c) {
    if (c == '{' || c == '[') {
      return 1;
    }
    return c == '}' || c == ']' ? -1 : 0;
  }

  private static boolean[] quoted(String text) {
    boolean[] inside = new boolean[text.length()];
    char quote = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quote == 0 && (c == '"' || c == '\'')) {
        quote = c;
        inside[i] = true;
      } else if (quote != 0) {
        inside[i] = true;
        quote =
            text.charAt(i) == quote && (quote == '\'' || text.charAt(i - 1) != '\\') ? 0 : quote;
      }
    }
    return inside;
  }

  private Parts parts(int at) {
    Matcher line = KEY_LINE.matcher(lines.get(at));
    if (!line.matches()) {
      throw new IllegalArgumentException("Not a key line: " + lines.get(at).strip());
    }
    String rest = line.group(4) == null ? "" : line.group(4);
    int comment = commentAt(rest);
    return new Parts(
        line.group(1),
        line.group(2),
        line.group(3) == null ? "" : line.group(3),
        rest.substring(0, comment).stripTrailing(),
        rest.substring(comment));
  }

  private static int commentAt(String rest) {
    boolean[] inside = quoted(rest);
    for (int i = 0; i < rest.length(); i++) {
      if (!inside[i]
          && rest.charAt(i) == '#'
          && (i == 0 || Character.isWhitespace(rest.charAt(i - 1)))) {
        return i;
      }
    }
    return rest.length();
  }

  private int end(int at) {
    int indent = indent(lines.get(at)).length();
    for (int i = at + 1; i < lines.size(); i++) {
      String line = lines.get(i);
      int own = indent(line).length();
      if (!isNote(line) && (own < indent || (own == indent && !line.strip().startsWith("- ")))) {
        return i;
      }
    }
    return lines.size();
  }

  private int trimmed(int from, int to) {
    int end = to;
    while (end > from && isNote(lines.get(end - 1))) {
      end--;
    }
    return end;
  }

  private static boolean isNote(String line) {
    return line.isBlank() || line.strip().startsWith("#");
  }

  private static String indent(String line) {
    return line.substring(0, line.length() - line.stripLeading().length());
  }
}
