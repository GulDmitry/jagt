package fitness.scan;

import fitness.core.Project;
import fitness.core.FitnessFunction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

final class Sentences {

  private static final Pattern NOT_PROSE = Pattern.compile("^(```|\\||#|<|>)");
  private static final Pattern LIST_ITEM = Pattern.compile("^([-*]|\\d+\\.)\\s+");

  private Sentences() {}

  static List<Sized> words(FitnessFunction function, Project project, List<Path> files) {
    List<Sized> sentences = new ArrayList<>();
    for (Path file : files) {
      for (String sentence : of(Sources.read(file))) {
        sentences.add(
            new Sized(
                Sources.relative(project, file) + ": " + sentence, sentence.split("\\s+").length));
      }
    }
    return sentences;
  }

  private static List<String> of(String document) {
    List<String> blocks = new ArrayList<>();
    StringBuilder block = new StringBuilder();
    boolean fenced = false;
    for (String line : document.lines().toList()) {
      String text = line.strip();
      boolean fence = text.startsWith("```");
      fenced ^= fence;
      boolean prose = !fenced && !fence && !text.isEmpty() && !NOT_PROSE.matcher(text).find();
      if (!prose || LIST_ITEM.matcher(text).find()) {
        blocks.add(block.toString());
        block.setLength(0);
      }
      if (prose) {
        block.append(' ').append(LIST_ITEM.matcher(text).replaceFirst(""));
      }
    }
    blocks.add(block.toString());
    return blocks.stream()
        .map(text ->
            text.replaceAll("`[^`]*`", "CODE").replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1"))
        .flatMap(text -> Arrays.stream(text.split("(?<=[.!?;:])\\s+| · ")))
        .map(String::strip)
        .filter(sentence -> !sentence.isEmpty())
        .toList();
  }
}
