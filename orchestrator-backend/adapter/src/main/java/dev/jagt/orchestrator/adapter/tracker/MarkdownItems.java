package dev.jagt.orchestrator.adapter.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The work items of a Markdown checklist, in file order. */
final class MarkdownItems {

  static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9]+-\\d+");

  private static final Pattern ITEM_LINE =
      Pattern.compile("^- \\[([ xX])\\]\\s+\\**\\s*(" + KEY.pattern() + ")\\b\\s*[—–-]?\\s*(.*)$");

  record Item(String key, String title, String status, String body) {

    String text() {
      return body.isEmpty() ? title : title + "\n" + body;
    }
  }

  private MarkdownItems() {}

  static List<Item> parse(String markdown) {
    List<Item> items = new ArrayList<>();
    Matcher open = null;
    List<String> body = new ArrayList<>();
    for (String line : markdown.split("\\R", -1)) {
      boolean boundary = line.startsWith("- [") || line.startsWith("#");
      if (boundary && open != null) {
        items.add(item(open, body));
      }
      if (boundary) {
        body.clear();
        Matcher next = ITEM_LINE.matcher(line);
        open = next.matches() ? next : null;
      } else if (open != null) {
        body.add(line);
      }
    }
    if (open != null) {
      items.add(item(open, body));
    }
    return List.copyOf(items);
  }

  static Optional<Item> find(List<Item> items, String key) {
    return items.stream().filter(item -> item.key().equalsIgnoreCase(key.strip())).findFirst();
  }

  private static Item item(Matcher line, List<String> body) {
    String status = " ".equals(line.group(1)) ? "open" : "done";
    String title = line.group(3).replaceFirst("\\**\\s*$", "").strip();
    String text = String.join("\n", body).replaceFirst("^(\\s*\\n)+", "").stripTrailing();
    return new Item(line.group(2), title, status, text);
  }
}
