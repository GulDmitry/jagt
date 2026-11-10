package dev.jagt.orchestrator.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Typed reads of what a case file's YAML holds, each copied element by element. */
final class Yamls {

  private Yamls() {}

  static Map<String, Object> load(String yaml) {
    return map(new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml));
  }

  static Map<String, Object> map(@Nullable Object value) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) Objects.requireNonNull(value)).entrySet()) {
      copy.put((String) entry.getKey(), entry.getValue());
    }
    return copy;
  }

  static Map<String, Object> mapOrEmpty(@Nullable Object value) {
    return value == null ? Map.of() : map(value);
  }

  static List<Object> list(@Nullable Object value) {
    return List.copyOf((List<?>) Objects.requireNonNull(value));
  }

  static List<Object> listOrEmpty(@Nullable Object value) {
    return value == null ? List.of() : list(value);
  }

  static String text(@Nullable Object value) {
    return String.valueOf(Objects.requireNonNull(value));
  }

  static List<String> texts(@Nullable Object value) {
    return list(value).stream().map(Yamls::text).toList();
  }

  static Map<String, String> textsByKey(@Nullable Object value) {
    Map<String, String> copy = new LinkedHashMap<>();
    map(value).forEach((key, one) -> copy.put(key, text(one)));
    return copy;
  }
}
