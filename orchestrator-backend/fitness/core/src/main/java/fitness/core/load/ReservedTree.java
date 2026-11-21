package fitness.core.load;

import fitness.core.Section;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReservedTree {

  private static final String LAYOUT =
      """
      technical.maintainability = modularity reusability analysability modifiability testability
      technical.compatibility = co-existence interoperability
      technical.usability = appropriateness-recognisability learnability operability \
      user-error-protection user-interface-aesthetics accessibility
      technical.portability = adaptability installability replaceability
      data.data-quality = accuracy completeness consistency credibility currentness
      security.security = confidentiality integrity non-repudiation accountability authenticity
      operational.performance-efficiency = time-behaviour resource-utilisation capacity
      operational.reliability = maturity availability fault-tolerance recoverability
      """;

  private static final List<Section> SECTIONS = parse();

  private static final Set<String> GOVERNS =
      Set.of(
          "coupling",
          "abstractness-instability",
          "import-direction",
          "complexity",
          "license",
          "accessibility",
          "package-dependency",
          "class-dependency",
          "inheritance",
          "annotation",
          "layer",
          "linter",
          "communication",
          "chaos",
          "security-scan",
          "size",
          "count");

  private ReservedTree() {}

  public static List<Section> sections() {
    return SECTIONS;
  }

  public static Set<String> dimensions() {
    return SECTIONS.stream().map(Section::dimension).collect(Collectors.toSet());
  }

  public static Set<String> characteristics(String dimension) {
    return SECTIONS.stream()
        .filter(section -> section.dimension().equals(dimension))
        .map(Section::characteristic)
        .collect(Collectors.toSet());
  }

  public static Set<String> subcharacteristics(String dimension, String characteristic) {
    return SECTIONS.stream()
        .filter(section -> section.dimension().equals(dimension))
        .filter(section -> section.characteristic().equals(characteristic))
        .map(Section::subcharacteristic)
        .collect(Collectors.toSet());
  }

  public static Set<String> governs() {
    return GOVERNS;
  }

  private static List<Section> parse() {
    return LAYOUT
        .lines()
        .flatMap(
            line -> {
              String[] sides = line.split(" = ");
              String[] head = sides[0].split("\\.");
              return Arrays.stream(sides[1].trim().split(" "))
                  .map(sub -> new Section(head[0], head[1], sub));
            })
        .toList();
  }
}
