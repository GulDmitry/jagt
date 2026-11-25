package dev.jagt.orchestrator.task.launch;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * A task IS its branch, so anything git accepts as a branch name is a task name — the rules of
 * {@code git check-ref-format --branch} in one pattern. Such a name may still not be a file or a
 * terminal session: `/` nests a directory and a quote reaches a shell, so {@link #slug(String)} is
 * the flat form.
 */
public final class TaskName {

  /**
   * One directory component holds 255 bytes, and the slug is followed by `-<project key>-deploy`.
   */
  private static final int MAX = 180;

  /**
   * What {@code git check-ref-format} refuses outright, on top of every control character and the
   * space.
   */
  private static final String REFUSED_BY_GIT = "~^:?*[\\";

  private static final String REFNAME_CHAR =
      "[^\\x00-\\x20\\x7f" + Pattern.quote(REFUSED_BY_GIT) + "]";

  private static final Pattern VALID =
      Pattern.compile(
          "(?!-)(?!/)(?!@$)(?!HEAD$)(?!.*(^|/)\\.)(?!.*\\.lock(/|$))(?!.*\\.\\.)(?!.*@\\{)(?!.*//)"
              + REFNAME_CHAR
              + "{1,"
              + MAX
              + "}(?<![./])");
  private static final Pattern NOT_PLAIN = Pattern.compile("[^A-Za-z0-9_-]");

  /** Long enough to read the task off a branch listing, short enough to leave the suffixes room. */
  private static final int WRITTEN_MAX = 40;

  private static final int SUMMARY_WORDS = 4;

  private record Refusal(Predicate<String> applies, String reason) {}

  private static final List<Refusal> REFUSALS =
      List.of(
          new Refusal(name -> name.startsWith("-"), "it starts with '-'"),
          new Refusal(
              name -> name.startsWith("/") || name.endsWith("/"), "it starts or ends with '/'"),
          new Refusal(name -> name.contains("//"), "it has an empty part ('//')"),
          new Refusal(name -> name.endsWith("."), "it ends with '.'"),
          new Refusal(name -> name.contains(".."), "'..' is not allowed"),
          new Refusal(
              name -> name.startsWith(".") || name.contains("/."), "a part of it starts with '.'"),
          new Refusal(
              name -> name.endsWith(".lock") || name.contains(".lock/"),
              "a part of it ends with '.lock'"),
          new Refusal(name -> name.contains("@{"), "'@{' is not allowed"),
          new Refusal(name -> name.length() > MAX, "it is longer than " + MAX + " characters"));

  private TaskName() {}

  private static boolean isValid(@Nullable String name) {
    return name != null && VALID.matcher(name).matches();
  }

  /**
   * A branch name made of what a human wrote, for a task no tracker named. Null when their words
   * leave nothing a branch can be called.
   */
  public static @Nullable String from(@Nullable String text) {
    if (text == null) {
      return null;
    }
    return capped(plain(text.strip().lines().findFirst().orElse("")));
  }

  /**
   * A branch name made of the few words a model said the work does, {@value #SUMMARY_WORDS} at
   * most. Null when they leave nothing a branch can be called.
   */
  public static @Nullable String summarised(@Nullable String words) {
    if (words == null) {
      return null;
    }
    return capped(
        Arrays.stream(plain(words).split("-"))
            .limit(SUMMARY_WORDS)
            .collect(Collectors.joining("-")));
  }

  private static String plain(String line) {
    return NOT_PLAIN
        .matcher(line.toLowerCase(Locale.ROOT))
        .replaceAll("-")
        .replaceAll("-{2,}", "-")
        .replaceAll("^-|-$", "");
  }

  private static @Nullable String capped(String slug) {
    String result = slug;
    if (result.length() > WRITTEN_MAX) {
      String whole = result.substring(0, WRITTEN_MAX).replaceAll("-[^-]*$", "");
      result = whole.isEmpty() ? result.substring(0, WRITTEN_MAX) : whole;
    }
    return result.isEmpty() ? null : result;
  }

  /**
   * The same task as ONE plain name: a worktree directory, a terminal session, a socket, a temp
   * file. A dot goes too — a session host may read it as {@code session:window.pane}.
   */
  public static String slug(String name) {
    return NOT_PLAIN.matcher(name).replaceAll("-");
  }

  public static void require(String name, String argument) {
    String reason = unusableReason(name);
    if (reason != null) {
      throw new IllegalArgumentException(
          "Argument '"
              + argument
              + "' is not a branch name, and a task"
              + " IS its branch: "
              + reason
              + "; got: "
              + name);
    }
  }

  /** Names the ONE thing that makes {@code name} unusable, or null when it is usable. */
  public static @Nullable String unusableReason(@Nullable String name) {
    if (isValid(name)) {
      return null;
    }
    if (name == null || name.isEmpty()) {
      return "it is empty";
    }
    String offender =
        name.codePoints()
            .filter(TaskName::refusedByGit)
            .mapToObj(TaskName::readable)
            .findFirst()
            .orElse(null);
    if (offender != null) {
      return "'" + offender + "' is not allowed";
    }
    return REFUSALS.stream()
        .filter(refusal -> refusal.applies().test(name))
        .map(Refusal::reason)
        .findFirst()
        .orElse("git reserves that name");
  }

  private static boolean refusedByGit(int codePoint) {
    return codePoint <= 0x20 || codePoint == 0x7f || REFUSED_BY_GIT.indexOf(codePoint) >= 0;
  }

  private static String readable(int codePoint) {
    return codePoint < 0x20 || codePoint == 0x7f
        ? "\\u%04x".formatted(codePoint)
        : Character.toString(codePoint);
  }
}
