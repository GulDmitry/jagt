package fitness.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Modules {

  private static final String EXPOSES = "exposes";

  private Modules() {}

  static Map<String, List<String>> read(Object raw) {
    Map<String, List<String>> modules = new LinkedHashMap<>();
    if (raw == null) {
      return modules;
    }
    Types.map("project.modules", raw)
        .forEach((name, declared) -> modules.put(name, exposes("project.modules." + name, declared)));
    return modules;
  }

  private static List<String> exposes(String key, Object declared) {
    if (!(declared instanceof Map<?, ?> map) || !map.containsKey(EXPOSES)) {
      throw new FitnessConfigException(Reason.MODULE_WITHOUT_EXPOSES, key);
    }
    for (Object entry : map.keySet()) {
      if (!EXPOSES.equals(entry)) {
        throw new FitnessConfigException(Reason.UNKNOWN_KEY, key + "." + entry);
      }
    }
    if (!(map.get(EXPOSES) instanceof List<?> list)) {
      throw new FitnessConfigException(Reason.WRONG_TYPE, key + "." + EXPOSES);
    }
    return list.stream().map(item -> Types.string(key + "." + EXPOSES, item)).toList();
  }
}
