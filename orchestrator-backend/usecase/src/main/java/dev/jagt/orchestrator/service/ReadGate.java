package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.util.PatternMatchUtils;

/**
 * What a headless read may call, answered tool by tool: the human's own allow rules cannot widen
 * it. A file tool stays inside the scope's roots, the shell runs one read-only git command or one
 * GET of a named environment, and any other tool must be named.
 */
public final class ReadGate {

  private static final String GLOB = "Glob";
  private static final Set<String> FILE_TOOLS = Set.of("Read", "Grep", GLOB);
  private static final List<String> PATH_KEYS = List.of("file_path", "path");
  private static final String SHELL_TOOL = "Bash";
  private static final String WEB_TOOL = "WebFetch";

  /**
   * WebFetch reaches no host without a dot and no plain http, so an environment is read with one
   * bare GET too.
   */
  private static final Pattern CURL_GET =
      Pattern.compile(
          "curl(?= .*(?<= )-sS(?: |$))(?: -sS| -i)*" + " ([A-Za-z0-9._:/?#=%-]+)(?: -sS| -i)*");

  static final Pattern PARENT = Pattern.compile("(^|/)\\.\\.(/|$)");

  /** A brace builds a parent out of pieces no single one of which is {@code ..}. */
  private static final Pattern GLOB_ESCAPE = Pattern.compile("[{}]|\\.\\.");

  private ReadGate() {}

  /** Why the call is refused, or empty when it may go on to the CLI's own permission rules. */
  public static Optional<String> refusal(
      ReadScope read, String tool, Map<String, Object> input, @Nullable String cwd) {
    List<String> allowing = read.tools().stream().filter(named -> names(named, tool)).toList();
    if (allowing.contains(tool)
        || (!allowing.isEmpty() && read.writes().stream().noneMatch(write -> names(write, tool)))) {
      return Optional.empty();
    }
    Map<String, Object> arguments = input == null ? Map.of() : input;
    Path here =
        cwd == null || cwd.isBlank()
            ? read.roots().stream().findFirst().orElse(Path.of("/"))
            : Path.of(cwd);
    if (FILE_TOOLS.contains(tool)) {
      return fileRefusal(read, arguments, here, GLOB.equals(tool));
    }
    if (SHELL_TOOL.equals(tool)) {
      return shellRefusal(read, String.valueOf(arguments.getOrDefault("command", "")), here);
    }
    if (WEB_TOOL.equals(tool) && !read.fetchable().isEmpty()) {
      return fetchRefusal(read, String.valueOf(arguments.getOrDefault("url", "")));
    }
    return given(tool);
  }

  private static Optional<String> given(String tool) {
    return Optional.of("jagt refuses " + tool + " in a read: only the tools it was given may run.");
  }

  private static Optional<String> shellRefusal(ReadScope read, String command, Path here) {
    if (command.startsWith("curl ") && !read.fetchable().isEmpty()) {
      var get = CURL_GET.matcher(command);
      return get.matches()
          ? fetchRefusal(read, get.group(1))
          : Optional.of(
              "jagt refuses " + command + ": a read runs curl -sS [-i] <url> and nothing more.");
    }
    return read.git() ? GitReadLine.refusal(read, command, here) : given(SHELL_TOOL);
  }

  private static Optional<String> fetchRefusal(ReadScope read, String url) {
    boolean listed =
        read.fetchable().stream()
            .anyMatch(
                environment ->
                    url.equals(environment)
                        || (url.startsWith(environment)
                            && "/?#".indexOf(url.charAt(environment.length())) >= 0)
                        || (url.startsWith(environment) && environment.endsWith("/")));
    return listed
        ? Optional.empty()
        : Optional.of(
            "jagt refuses fetching "
                + url
                + ": a read reaches only the environments the role names.");
  }

  /** A bare server names none of its tools. */
  private static boolean names(String named, String tool) {
    return PatternMatchUtils.simpleMatch(named, tool);
  }

  /**
   * A search naming no path searches where the call runs; a glob's own pattern may name a
   * directory.
   */
  private static Optional<String> fileRefusal(
      ReadScope read, Map<String, Object> arguments, Path here, boolean glob) {
    String path =
        PATH_KEYS.stream()
            .map(arguments::get)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .findFirst()
            .orElse(".");
    if (!inside(read, here, path)) {
      return Optional.of(
          "jagt refuses reading " + path + ": a read stays inside the round's worktrees.");
    }
    Object pattern = arguments.get("pattern");
    if (glob && pattern != null && !globInside(read, here, pattern.toString())) {
      return Optional.of(
          "jagt refuses the pattern " + pattern + ": a read stays inside the round's worktrees.");
    }
    return Optional.empty();
  }

  private static boolean globInside(ReadScope read, Path here, String pattern) {
    if (GLOB_ESCAPE.matcher(pattern).find() || pattern.startsWith("~")) {
      return false;
    }
    return !pattern.startsWith("/") || inside(read, here, pattern.split("[*?\\[{]", 2)[0]);
  }

  static boolean inside(ReadScope read, Path here, String path) {
    if (path.startsWith("~")) {
      return false;
    }
    Path target = real(here.resolve(path));
    return read.roots().stream().map(ReadGate::real).anyMatch(target::startsWith);
  }

  static boolean isRoot(ReadScope read, Path here, String path) {
    Path target = real(here.resolve(path));
    return read.roots().stream().map(ReadGate::real).anyMatch(target::equals);
  }

  /** A link inside a worktree may point anywhere, so a path is judged where it really leads. */
  private static Path real(Path path) {
    Path absolute = path.toAbsolutePath().normalize();
    try {
      return absolute.toRealPath();
    } catch (IOException e) {
      Path parent = absolute.getParent();
      return parent == null ? absolute : real(parent).resolve(absolute.getFileName());
    }
  }
}
