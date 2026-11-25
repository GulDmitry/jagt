package fitness.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import fitness.core.Measurement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class DuplicateSimpleNames {

  private DuplicateSimpleNames() {}

  static Measurement measure(List<JavaClass> owners) {
    Map<String, List<String>> byName = new TreeMap<>();
    owners.stream()
        .filter(type -> !type.isNestedClass() && !type.getSimpleName().equals("package-info"))
        .forEach(
            type ->
                byName.computeIfAbsent(type.getSimpleName(), name -> new ArrayList<>())
                    .add(type.getName()));
    return Measurement.of(
        byName.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(
                entry ->
                    entry.getKey()
                        + ": "
                        + entry.getValue().stream().sorted().collect(Collectors.joining(", ")))
            .toList());
  }
}
