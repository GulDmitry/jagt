package fitness.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.PackageMatcher;
import fitness.core.Engine;
import fitness.core.FitnessConfigException;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import fitness.core.Reason;
import fitness.core.run.SourceRoots;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class ArchUnitEngine implements Engine {

  private static final String KIND = "kind";
  private static final String ANNOTATED = "annotated";

  private final ClassDirectories directories = new ClassDirectories();

  @Override
  public String name() {
    return "archunit";
  }

  @Override
  public Map<String, Set<String>> vocabulary() {
    return Map.ofEntries(
        Map.entry(KIND, Set.of("class", "field", "method")),
        Map.entry("set", Set.of()),
        Map.entry("interface", Set.of("true", "false")),
        Map.entry("static", Set.of("true", "false")),
        Map.entry("mutable", Set.of("true", "false")),
        Map.entry("assignableTo", Set.of()),
        Map.entry(ANNOTATED, Set.of()),
        Map.entry("calls", Set.of()),
        Map.entry("without", Set.of()),
        Map.entry("except", Set.of()),
        Map.entry("measure", Set.of("constructor-parameters", "module-isolation", "single-implementer", "flag-arguments", "static-helpers", "duplicate-simple-names")),
        Map.entry("package", Set.of()));
  }

  @Override
  public Set<String> needs(FitnessFunction function) {
    return Set.of("archunit");
  }

  @Override
  public Map<String, String> libraries() {
    return Map.of("archunit", "com.tngtech.archunit.core.importer.ClassFileImporter");
  }

  @Override
  public void validate(FitnessFunction function, Project project) {
    SourceRoots.validate(function, project);
    if (function.selectors().get("except") instanceof String names) {
      JavaClasses imported =
          directories.of(project, String.valueOf(function.selectors().getOrDefault("set", "main")));
      Pattern.compile(",")
          .splitAsStream(names)
          .filter(entry -> !imported.isEmpty() && imported.stream().noneMatch(matches(entry, project.basePackage())))
          .findFirst()
          .ifPresent(
              entry -> {
                throw new FitnessConfigException(
                    Reason.UNKNOWN_EXCEPT_ENTRY,
                    function.section() + "." + function.id() + ".except: " + entry);
              });
    }
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    Map<String, Object> selectors = function.selectors();
    String set = String.valueOf(selectors.getOrDefault("set", "main"));
    JavaClasses imported = directories.of(project, set);
    if ("module-isolation".equals(selectors.get("measure"))) {
      return ModuleIsolation.measure(imported, project);
    }
    List<JavaClass> owners =
        imported.stream()
            .filter(owner(selectors, project.basePackage()))
            .toList();
    if (selectors.get("calls") instanceof String call) {
      return Measurement.of(Members.calls(owners, call, selectors.get("without")));
    }
    return switch (String.valueOf(selectors.getOrDefault(KIND, "class"))) {
      case "field" -> Measurement.of(Members.fields(owners, selectors));
      case "method" -> Measurement.of(Members.methods(owners, selectors));
      default -> classes(function, owners);
    };
  }

  private static Measurement classes(FitnessFunction function, List<JavaClass> owners) {
    Map<String, Object> selectors = function.selectors();
    if ("flag-arguments".equals(selectors.get("measure"))) {
      return FlagArguments.measure(owners, String.valueOf(selectors.get(ANNOTATED)));
    }
    if ("duplicate-simple-names".equals(selectors.get("measure"))) {
      return DuplicateSimpleNames.measure(owners);
    }
    if ("static-helpers".equals(selectors.get("measure"))) {
      return StaticHelpers.measure(owners, String.valueOf(selectors.get(ANNOTATED)));
    }
    List<JavaClass> annotated =
        owners.stream()
            .filter(
                type ->
                    !(selectors.get(ANNOTATED) instanceof String annotation)
                        || type.isMetaAnnotatedWith(annotation))
            .toList();
    if ("constructor-parameters".equals(selectors.get("measure"))) {
      return parameters(function, annotated);
    }
    if ("single-implementer".equals(selectors.get("measure"))) {
      return SingleImplementers.measure(annotated);
    }
    return Measurement.of(
        annotated.stream()
            .filter(type -> !type.isNestedClass())
            .map(JavaClass::getName)
            .toList());
  }

  private static Measurement parameters(FitnessFunction function, List<JavaClass> classes) {
    long limit = function.threshold().limit();
    Map<String, Integer> widest = new TreeMap<>();
    for (JavaClass type : classes) {
      int most =
          type.getConstructors().stream()
              .mapToInt(c -> c.getRawParameterTypes().size())
              .max()
              .orElse(0);
      widest.put(type.getName(), most);
    }
    List<String> over =
        widest.entrySet().stream()
            .filter(e -> e.getValue() > limit)
            .map(e -> e.getValue() + " " + e.getKey())
            .toList();
    return new Measurement(widest.values().stream().mapToInt(Integer::intValue).max().orElse(0), over);
  }

  private static Predicate<JavaClass> owner(Map<String, Object> selectors, String base) {
    Predicate<JavaClass> selected = type -> true;
    if (selectors.get("interface") instanceof Boolean wanted) {
      selected = selected.and(type -> wanted ? type.isInterface() : isConcrete(type));
    }
    if (selectors.get("assignableTo") instanceof String supertype) {
      String name = qualified(base, supertype);
      selected = selected.and(type -> type.isAssignableTo(name) && !type.getName().equals(name));
    }
    if (selectors.get("package") instanceof String pattern) {
      PackageMatcher matcher = PackageMatcher.of(qualified(base, pattern));
      selected = selected.and(type -> matcher.matches(type.getPackageName()));
    }
    if (selectors.get("except") instanceof String names) {
      selected = selected.and(Predicate.not(excluded(names, base)));
    }
    return selected;
  }

  private static Predicate<JavaClass> excluded(String names, String base) {
    List<String> entries = Pattern.compile(",").splitAsStream(names).toList();
    return type -> entries.stream().anyMatch(entry -> matches(entry, base).test(type));
  }

  private static Predicate<JavaClass> matches(String entry, String base) {
    return type ->
        entry.contains("..")
            ? PackageMatcher.of(qualified(base, entry)).matches(type.getPackageName())
            : entry.equals(type.getSimpleName());
  }

  private static String qualified(String base, String name) {
    return base.isEmpty() ? name : base + "." + name;
  }

  private static boolean isConcrete(JavaClass type) {
    return !type.isInterface() && !type.getModifiers().contains(JavaModifier.ABSTRACT);
  }
}
