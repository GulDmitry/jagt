package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.protocol.Schema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Compiles a {@code roles/<id>/role.yml} into a {@link RoleDefinition}; every word it holds is from
 * a closed vocabulary.
 */
public final class RoleDefinitions {

  private static final Set<String> TRIGGERS =
      Set.of("deployed", "pipelines-green", "environment-named");
  private static final Set<String> ONCE_PER = Set.of("deploy");
  private static final Set<String> OUTCOMES =
      Set.of("round", "question", "wait", "dispute", "stop", "label");

  private RoleDefinitions() {}

  public static RoleDefinition fromFile(Path file) {
    return fromFiles(List.of(file));
  }

  /**
   * Each file over the one before it: a mapping merges key by key, nested ones too; any other value
   * replaces.
   */
  public static RoleDefinition fromFiles(List<Path> bottomFirst) {
    return compile(tree(bottomFirst));
  }

  static @Nullable Object tree(List<Path> bottomFirst) {
    @Nullable Object tree = null;
    for (Path file : bottomFirst) {
      try {
        tree = over(tree, load(Files.readString(file)));
      } catch (IOException unread) {
        throw new UncheckedIOException(unread);
      }
    }
    return tree;
  }

  public static RoleDefinition compile(String yaml) {
    return compile(load(yaml));
  }

  private static RoleDefinition compile(@Nullable Object tree) {
    if (!(tree instanceof Map<?, ?> document)) {
      throw new RoleInvalid(
          "a role.yml is a mapping of id, name, trigger, outcomes, tools, answer and decline");
    }
    String id = text(document, "id");
    List<String> outcomes = words(document, "outcomes", OUTCOMES);
    if (outcomes.isEmpty()) {
      throw new RoleInvalid(id + ": its read has no exit, for no outcome ends it");
    }
    List<String> trigger = words(document, "trigger", TRIGGERS);
    if (!trigger.contains("deployed")) {
      throw new RoleInvalid(
          id + ": its trigger names deployed, or a check would open before the change is live");
    }
    return new RoleDefinition(
        id,
        text(document, "name"),
        text(document, "model"),
        new RoleDefinition.Trigger(trigger, word(document, "oncePer", ONCE_PER)),
        new RoleDefinition.Replies(
            outcomes, schema(document, "answer"), schema(document, "decline")),
        tools(map(document, "tools")),
        new RoleDefinition.Checking(
            Boolean.TRUE.equals(document.get("jobs")), strings(document, "readSteps")));
  }

  private static Object load(String yaml) {
    return new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
  }

  private static @Nullable Object over(@Nullable Object base, @Nullable Object top) {
    if (base instanceof Map<?, ?> under && top instanceof Map<?, ?> above) {
      Map<Object, Object> merged = new LinkedHashMap<>(under);
      above.forEach((key, value) -> merged.put(key, over(under.get(key), value)));
      return merged;
    }
    return top == null ? base : top;
  }

  private static RoleDefinition.Tools tools(Map<?, ?> tools) {
    List<String> old = Stream.of("cli", "allow", "refuse").filter(tools::containsKey).toList();
    if (!old.isEmpty()) {
      throw new RoleInvalid(
          "tools."
              + String.join(", tools.", old)
              + " is no longer read: a role names its tools as "
              + "tools.shell, tools.refuseShell and tools.refuseRead, command and path globs");
    }
    return new RoleDefinition.Tools(
        strings(tools, "shell"),
        strings(tools, "refuseShell"),
        strings(tools, "refuseRead"),
        Boolean.TRUE.equals(tools.get("git")),
        "environments".equals(tools.get("fetch")),
        strings(tools, "mcp"));
  }

  private static Schema schema(Map<?, ?> document, String key) {
    Schema schema = Schema.answer();
    for (Object item : list(document, key)) {
      if (!(item instanceof Map<?, ?> field) || field.size() < 1) {
        throw new RoleInvalid(key + ": every entry is a mapping naming one field");
      }
      if (field.containsKey("required")) {
        schema.required(String.valueOf(field.get("required")), "string", null);
      } else if (field.containsKey("text")) {
        schema.text(String.valueOf(field.get("text")), null);
      } else if (field.containsKey("texts")) {
        schema.texts(String.valueOf(field.get("texts")), null);
      } else if (field.containsKey("records")) {
        schema.records(
            String.valueOf(field.get("records")), null, strings(field, "fields"), choices(field));
      } else {
        throw new RoleInvalid(
            key + ": an entry is required, text, texts or records, not " + field.keySet());
      }
    }
    return schema;
  }

  private static Map<String, List<String>> choices(Map<?, ?> field) {
    Map<String, List<String>> choices = new LinkedHashMap<>();
    if (field.get("choices") instanceof Map<?, ?> named) {
      named.forEach(
          (name, values) -> choices.put(String.valueOf(name), strings(Map.of("v", values), "v")));
    }
    return choices;
  }

  private static String text(Map<?, ?> document, String key) {
    Object value = document.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      throw new RoleInvalid("a role.yml names its " + key);
    }
    return String.valueOf(value);
  }

  private static String word(Map<?, ?> document, String key, Set<String> vocabulary) {
    String value = text(document, key);
    if (!vocabulary.contains(value)) {
      throw new RoleInvalid(
          key + " is one of " + vocabulary.stream().sorted().toList() + ", not " + value);
    }
    return value;
  }

  private static List<String> words(Map<?, ?> document, String key, Set<String> vocabulary) {
    List<String> words = strings(document, key);
    words.stream()
        .filter(word -> !vocabulary.contains(word))
        .findFirst()
        .ifPresent(
            unknown -> {
              throw new RoleInvalid(
                  key + " is of " + vocabulary.stream().sorted().toList() + ", not " + unknown);
            });
    return words;
  }

  private static Map<?, ?> map(Map<?, ?> document, String key) {
    return document.get(key) instanceof Map<?, ?> found ? found : Map.of();
  }

  private static List<?> list(Map<?, ?> document, String key) {
    return document.get(key) instanceof List<?> found ? found : List.of();
  }

  private static List<String> strings(Map<?, ?> document, String key) {
    List<String> strings = new ArrayList<>();
    list(document, key).forEach(item -> strings.add(String.valueOf(item)));
    return strings;
  }
}
