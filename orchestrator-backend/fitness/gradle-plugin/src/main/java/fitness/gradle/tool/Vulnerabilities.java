package fitness.gradle.tool;

import groovy.json.JsonSlurper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The known vulnerabilities in a scanner's JSON report, one item per package and advisory. */
public final class Vulnerabilities {

  private Vulnerabilities() {}

  public static List<String> parse(String json) {
    List<String> found = new ArrayList<>();
    for (Object result : list(map(new JsonSlurper().parseText(json)).get("results"))) {
      for (Object entry : list(map(result).get("packages"))) {
        Map<?, ?> pack = map(entry);
        Map<?, ?> identity = map(pack.get("package"));
        for (Object advisory : list(pack.get("vulnerabilities"))) {
          found.add(identity.get("name") + "@" + identity.get("version") + " " + map(advisory).get("id"));
        }
      }
    }
    return found;
  }

  private static Map<?, ?> map(Object value) {
    return value instanceof Map<?, ?> map ? map : Map.of();
  }

  private static List<?> list(Object value) {
    return value instanceof List<?> list ? list : List.of();
  }
}
