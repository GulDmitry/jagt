package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.CodeHostCommands;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A code host's CLI runs only as a read, however it is placed; a quoted span is data unless a shell
 * runs it.
 */
final class HostCliLine {

  private static final Pattern HOST_METHOD = Pattern.compile("(-X|--method=?)(.*)");

  /**
   * The only options an {@code api} read may carry; anything else, a body or a bundle, makes it a
   * write.
   */
  private static final Set<String> API_READ_FLAGS =
      Set.of(
          "--paginate",
          "--slurp",
          "-i",
          "--include",
          "--silent",
          "--verbose",
          "-q",
          "--jq",
          "-t",
          "--template",
          "-H",
          "--header",
          "--hostname",
          "--cache");

  private static final java.util.function.Predicate<String> SHOWS_A_TOKEN =
      Pattern.compile("-t|--show-token").asMatchPredicate();
  private static final Pattern METHOD_OVERRIDE = Pattern.compile("(?i)\\s*x-http-method");
  private static final Set<String> API_READ_VALUED =
      Set.of("-q", "--jq", "-t", "--template", "-H", "--header", "--hostname", "--cache");
  private static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_]\\w*=.*");
  private static final Set<String> WRAPPERS = Set.of("env", "command", "exec", "xargs");
  private static final Set<String> WRAPPER_OPTION_WITH_VALUE =
      Set.of("-u", "-C", "-S", "-a", "-n", "-I", "-L", "-P", "-s", "-d", "-E");

  private static final Set<String> REPO_FLAGS = Set.of("-R", "--repo", "--hostname");
  private static final Pattern GLUED_REPO = Pattern.compile("-R.+|--(repo|hostname)=.+");
  private static final int WRITE = -1;
  private static final Set<String> SHELLS =
      Set.of(
          "sh", "bash", "zsh", "dash", "ksh", "mksh", "csh", "tcsh", "fish", "watch", "ssh", "su",
          "sudo", "flock", "script");

  private final Pattern hostCli;
  private final Map<String, Set<String>> hostReads = new TreeMap<>();
  private final Set<String> groups = new HashSet<>(REPO_FLAGS);
  private final List<String> commands;

  HostCliLine(List<CodeHostCommands> hosts) {
    commands = hosts.stream().map(CodeHostCommands::command).sorted().toList();
    hostCli =
        Pattern.compile("(\\S*/)?(" + String.join("|", commands) + ")", Pattern.CASE_INSENSITIVE);
    hosts.forEach(
        host -> {
          groups.addAll(host.groups());
          host.reads().forEach((group, verbs) -> hostReads.merge(group, verbs, HostCliLine::union));
        });
  }

  /** The CLIs' words, sorted. */
  List<String> commands() {
    return commands;
  }

  private static Set<String> union(Set<String> left, Set<String> right) {
    Set<String> both = new HashSet<>(left);
    both.addAll(right);
    return both;
  }

  /** What a host CLI may run, as the refusal names it. */
  String readsAllowed() {
    return new TreeMap<>(hostReads)
        .entrySet().stream()
            .map(read -> read.getKey() + " " + String.join("/", new TreeSet<>(read.getValue())))
            .collect(Collectors.joining(", ", "", ", api GET"));
  }

  boolean writes(String segment) {
    List<String> words = shellWords(segment);
    int command = commandPosition(words);
    for (int at = 0; at < words.size(); at++) {
      if (callsWrite(words, at, command) || runsLines(words, at)) {
        return true;
      }
    }
    return false;
  }

  private boolean callsWrite(List<String> words, int at, int command) {
    String next = at + 1 < words.size() ? words.get(at + 1) : "";
    boolean glued = GLUED_REPO.matcher(next).matches();
    boolean called =
        hostCli.matcher(words.get(at)).matches()
            && (at == command || groups.contains(next) || glued || next.contains("{"));
    return called && !reads(words, readsFrom(at, next, glued));
  }

  private static int readsFrom(int at, String next, boolean glued) {
    if (REPO_FLAGS.contains(next)) {
      return at + 3;
    }
    return glued ? at + 2 : at + 1;
  }

  private static int commandPosition(List<String> words) {
    int at = 0;
    while (at < words.size()) {
      if (ASSIGNMENT.matcher(words.get(at)).matches()) {
        at++;
      } else if (WRAPPERS.contains(words.get(at).replaceFirst(".*/", ""))) {
        at = afterWrapperOptions(words, at + 1);
      } else {
        break;
      }
    }
    return at;
  }

  private static int afterWrapperOptions(List<String> words, int from) {
    int at = from;
    while (at < words.size() && words.get(at).startsWith("-")) {
      at += WRAPPER_OPTION_WITH_VALUE.contains(words.get(at)) ? 2 : 1;
    }
    return at;
  }

  /**
   * A shell, {@code eval} and {@code env -S} may run a later quoted span as a line, so each one
   * holding a space is judged as one; a bare word is already judged where it stands.
   */
  private boolean runsLines(List<String> words, int at) {
    String word = words.get(at).replaceFirst(".*/", "");
    return (SHELLS.contains(word) || "eval".equals(word) || "env".equals(word))
        && words.subList(at + 1, words.size()).stream()
            .map(later -> later.replaceFirst("^(-S|--split-string=)", ""))
            .filter(later -> later.chars().anyMatch(Character::isWhitespace))
            .anyMatch(this::writesLine);
  }

  private boolean writesLine(String line) {
    return Arrays.stream(line.split("[;&|\\n]+")).anyMatch(this::writes);
  }

  private boolean reads(List<String> words, int from) {
    String command = from < words.size() ? words.get(from) : "";
    return "api".equals(command) ? readsApi(words, from) : readsGroup(words, from, command);
  }

  private boolean readsGroup(List<String> words, int from, String command) {
    return from + 1 < words.size()
        && hostReads.getOrDefault(command, Set.of()).contains(words.get(from + 1))
        && words.subList(from + 2, words.size()).stream().noneMatch(SHOWS_A_TOKEN);
  }

  private static boolean readsApi(List<String> words, int start) {
    for (int at = start; at < words.size(); at++) {
      if (!words.get(at).startsWith("-")) {
        continue;
      }
      at = apiOptionEnd(words, at);
      if (at == WRITE) {
        return false;
      }
    }
    return true;
  }

  /**
   * The index of the last word the read option at {@code at} consumed, or {@code WRITE} when it
   * makes a write.
   */
  private static int apiOptionEnd(List<String> words, int at) {
    String word = words.get(at);
    Matcher method = HOST_METHOD.matcher(word);
    if (method.matches()) {
      boolean detached = method.group(2).isEmpty() && at + 1 < words.size();
      String named = detached ? words.get(at + 1) : method.group(2);
      return "GET".equalsIgnoreCase(named) ? at : WRITE;
    }
    if (API_READ_VALUED.contains(word)) {
      boolean overridden =
          at + 1 < words.size() && METHOD_OVERRIDE.matcher(words.get(at + 1)).lookingAt();
      return overridden ? WRITE : at + 1;
    }
    return API_READ_FLAGS.contains(word.replaceFirst("=.*", "")) ? at : WRITE;
  }

  /** An unclosed quote keeps its mark, so what follows it never reads as a bare word. */
  private static List<String> shellWords(String segment) {
    Words words = new Words();
    segment.chars().forEach(c -> words.read((char) c));
    return words.finish();
  }

  private static final class Words {
    private final List<String> words = new ArrayList<>();
    private final StringBuilder word = new StringBuilder();
    private char quote;
    private boolean inWord;

    void read(char c) {
      if (quote != 0) {
        readQuoted(c);
      } else if (c == '\'' || c == '"') {
        quote = c;
        inWord = true;
      } else if (Character.isWhitespace(c)) {
        endWord();
      } else {
        word.append(c);
        inWord = true;
      }
    }

    private void readQuoted(char c) {
      if (c == quote) {
        quote = 0;
      } else {
        word.append(c);
      }
    }

    private void endWord() {
      if (inWord) {
        words.add(word.toString());
        word.setLength(0);
        inWord = false;
      }
    }

    List<String> finish() {
      if (quote != 0) {
        word.insert(0, quote);
      }
      if (inWord) {
        words.add(word.toString());
      }
      return words;
    }
  }
}
