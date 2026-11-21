package fitness.core.load;

import fitness.core.FitnessConfigException;
import fitness.core.Reason;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Types {

  private Types() {}

  static Map<String, Object> map(String key, Object value) {
    if (!(value instanceof Map<?, ?> raw)) {
      throw new FitnessConfigException(Reason.WRONG_TYPE, key);
    }
    Map<String, Object> typed = new LinkedHashMap<>();
    raw.forEach((name, child) -> typed.put(String.valueOf(name), child));
    return typed;
  }

  static String string(String key, Object value) {
    if (!(value instanceof String text)) {
      throw new FitnessConfigException(Reason.WRONG_TYPE, key);
    }
    return text;
  }

  static boolean bool(String key, Object value) {
    if (!(value instanceof Boolean flag)) {
      throw new FitnessConfigException(Reason.WRONG_TYPE, key);
    }
    return flag;
  }

  static long count(String key, Object value) {
    if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0) {
      throw new FitnessConfigException(Reason.WRONG_TYPE, key);
    }
    return ((Number) value).longValue();
  }
}
