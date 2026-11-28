package technical.maintainability.modularity;

import dev.jagt.orchestrator.JagtYmlSchema;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class ConfigKeyVendorsFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    List<String> named = new ArrayList<>();
    collect("", List.of(), JagtYmlSchema.generate(), named);
    return Measurement.of(named);
  }

  private static void collect(
      String path, List<String> ancestors, JsonNode node, List<String> named) {
    for (Map.Entry<String, JsonNode> property : node.path("properties").properties()) {
      String key = property.getKey();
      String full = path.isEmpty() ? key : path + "." + key;
      if (vendorOutsideItsBlock(key, ancestors)) {
        named.add("vendor named outside its own block: " + full);
      }
      List<String> inside = new ArrayList<>(ancestors);
      inside.add(key);
      collect(full, inside, property.getValue(), named);
    }
  }

  private static boolean vendorOutsideItsBlock(String key, List<String> ancestors) {
    String lower = key.toLowerCase(Locale.ROOT);
    return EdgeNamesFitness.VENDORS.stream()
        .filter(lower::contains)
        .anyMatch(vendor ->
            !lower.equals(vendor)
                && ancestors.stream().noneMatch(block -> block.equalsIgnoreCase(vendor)));
  }
}
