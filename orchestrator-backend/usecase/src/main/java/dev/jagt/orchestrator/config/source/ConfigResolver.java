package dev.jagt.orchestrator.config.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Expands {@code ${VAR}}, {@code ${VAR:default}} and {@code ${file:/path}} in a loaded document's
 * strings, then lets every {@code JAGT__a__b=value} variable set the key {@code a.b}.
 */
@RequiredArgsConstructor
public class ConfigResolver {

  private static final String PREFIX = "JAGT__";
  private static final String SEPARATOR = "__";
  private static final String FILE = "file:";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
  private static final Pattern FILE_PLACEHOLDER = Pattern.compile("\\$\\{file:([^}]+)}");

  /** What a read depended on, so a later one can tell whether any of it moved. */
  public record Resolution(
      @Nullable Object document,
      Map<String, @Nullable String> variables,
      Map<Path, String> files,
      Map<String, String> overrides) {}

  private final ConfigInputs inputs;

  public Resolution resolve(@Nullable Object document) {
    Map<String, String> environment = inputs.environment();
    Map<String, @Nullable String> variables = new HashMap<>();
    Map<Path, String> files = new HashMap<>();
    @Nullable Object expanded =
        expand(
            document,
            "",
            (key, text) ->
                replace(
                    PLACEHOLDER,
                    text,
                    inner -> valueOf(key, inner, environment, variables, files)));
    Map<String, String> overrides = overridesIn(environment);
    @Nullable Object result = overrides.isEmpty() ? expanded : applied(expanded, overrides);
    return new Resolution(result, variables, files, overrides);
  }

  public boolean holds(Resolution seen) {
    Map<String, String> environment = inputs.environment();
    return seen.variables().entrySet().stream()
            .allMatch(entry -> Objects.equals(environment.get(entry.getKey()), entry.getValue()))
        && overridesIn(environment).equals(seen.overrides())
        && seen.files().entrySet().stream()
            .allMatch(entry -> entry.getValue().equals(contentOrNull(entry.getKey())));
  }

  /** Only {@code ${file:…}}, the form Spring's own placeholders do not know. */
  public String expandFiles(String key, String text) {
    return replace(
        FILE_PLACEHOLDER, text, inner -> contentOf(key, FILE + inner, inner, new HashMap<>()));
  }

  /** Dotted key to value, the way a binder reads its keys. */
  public Map<String, String> dottedOverrides() {
    Map<String, String> dotted = new TreeMap<>();
    overridesIn(inputs.environment())
        .forEach((name, value) -> dotted.put(String.join(".", path(name)), value));
    return dotted;
  }

  private static @Nullable Object expand(
      @Nullable Object node, String key, BiFunction<String, String, String> text) {
    if (node instanceof Map<?, ?> map) {
      Map<Object, @Nullable Object> copy = new LinkedHashMap<>();
      map.forEach((name, value) ->
          copy.put(name, expand(value, key.isEmpty() ? "" + name : key + "." + name, text)));
      return copy;
    }
    if (node instanceof List<?> list) {
      List<@Nullable Object> copy = new ArrayList<>();
      for (int i = 0; i < list.size(); i++) {
        copy.add(expand(list.get(i), key + "[" + i + "]", text));
      }
      return copy;
    }
    return node instanceof String string ? text.apply(key, string) : node;
  }

  private static String replace(Pattern pattern, String text, Function<String, String> value) {
    Matcher matcher = pattern.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, Matcher.quoteReplacement(value.apply(matcher.group(1))));
    }
    return matcher.appendTail(out).toString();
  }

  private String valueOf(
      String key,
      String inner,
      Map<String, String> environment,
      Map<String, @Nullable String> variables,
      Map<Path, String> files) {
    if (inner.startsWith(FILE)) {
      return contentOf(key, inner, inner.substring(FILE.length()), files);
    }
    int colon = inner.indexOf(':');
    String name = colon < 0 ? inner : inner.substring(0, colon);
    String value = environment.get(name);
    variables.put(name, value);
    if (value != null) {
      return value;
    }
    if (colon < 0) {
      throw new ConfigValueRefused(
          key,
          "variable " + name,
          "is not set and has no default: write ${" + name + ":default}",
          null);
    }
    return inner.substring(colon + 1);
  }

  private String contentOf(String key, String input, String file, Map<Path, String> files) {
    Path path = Path.of(file);
    try {
      String content = cut(inputs.read(path));
      files.put(path, content);
      return content;
    } catch (IOException | RuntimeException unreadable) {
      throw new ConfigValueRefused(key, input, "cannot be read", unreadable);
    }
  }

  private @Nullable String contentOrNull(Path file) {
    try {
      return cut(inputs.read(file));
    } catch (IOException | RuntimeException unreadable) {
      return null;
    }
  }

  private static String cut(String content) {
    return content.endsWith("\r\n")
        ? content.substring(0, content.length() - 2)
        : content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
  }

  private static Map<String, String> overridesIn(Map<String, String> environment) {
    Map<String, String> overrides = new TreeMap<>();
    environment.forEach((name, value) -> {
      if (name.startsWith(PREFIX) && path(name).stream().noneMatch(String::isEmpty)) {
        overrides.put(name, value);
      }
    });
    return overrides;
  }

  private static List<String> path(String name) {
    return Arrays.asList(name.substring(PREFIX.length()).split(SEPARATOR, -1));
  }

  private static Object applied(@Nullable Object document, Map<String, String> overrides) {
    Map<Object, Object> root = new LinkedHashMap<>();
    if (document instanceof Map<?, ?> map) {
      root.putAll(map);
    }
    overrides.forEach((name, value) -> set(root, path(name), value));
    return root;
  }

  private static void set(Map<Object, Object> root, List<String> path, String value) {
    Map<Object, Object> at = root;
    for (String segment : path.subList(0, path.size() - 1)) {
      Map<Object, Object> child = new LinkedHashMap<>();
      if (at.get(segment) instanceof Map<?, ?> existing) {
        child.putAll(existing);
      }
      at.put(segment, child);
      at = child;
    }
    at.put(path.get(path.size() - 1), value);
  }
}
