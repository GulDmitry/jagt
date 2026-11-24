package dev.jagt.orchestrator.service.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The blocks of {@code review_replies.md}: a heading line, then the body whose {@code Author:} line
 * jagt gave. A block with no such line reads as a person's; kind {@code unknown} is one jagt could
 * not tell.
 */
public record ReplyBlocks(List<Block> blocks) {

  public static final Pattern BLOCK = Pattern.compile("(?ms)^## ([^\\n]*)\\n(.*?)(?=^## |\\z)");

  private static final Pattern AUTHOR =
      Pattern.compile("(?m)^Author: (person|bot|reviewer|unknown)\\b[ \\t]*([^\\n]*)$");

  public enum Kind {
    PERSON,
    BOT,
    REVIEWER,
    UNKNOWN
  }

  public record Block(Kind kind, String author) {}

  public static ReplyBlocks parse(String text) {
    List<Block> blocks = new ArrayList<>();
    Matcher found = BLOCK.matcher(text);
    while (found.find()) {
      Matcher author = AUTHOR.matcher(found.group(2));
      blocks.add(
          author.find()
              ? new Block(kind(author.group(1)), author.group(2).strip())
              : new Block(Kind.PERSON, ""));
    }
    return new ReplyBlocks(
        blocks.isEmpty() && !text.isBlank() ? List.of(new Block(Kind.PERSON, "")) : blocks);
  }

  private static Kind kind(String word) {
    return Kind.valueOf(word.toUpperCase(Locale.ROOT));
  }
}
