package technical.maintainability.testability;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import dev.jagt.orchestrator.CollaboratorOwners;
import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

public final class InstanceStateFitness implements Fitness {

  private static final List<String> COMPONENT_PACKAGES =
      List.of(SourceRoots.BASE_PACKAGE + ".job", SourceRoots.BASE_PACKAGE + ".service");
  private static final List<String> HOLDERS =
      List.of(
          "java.util.concurrent.atomic.",
          "java.util.concurrent.ConcurrentHashMap",
          "java.util.concurrent.CopyOnWrite",
          "java.util.Collection",
          "java.util.List",
          "java.util.Set",
          "java.util.Map",
          "java.util.Queue",
          "java.util.Deque",
          "java.util.HashMap",
          "java.util.LinkedHashMap",
          "java.util.TreeMap",
          "java.util.ArrayList",
          "java.util.LinkedList",
          "java.util.HashSet",
          "java.util.ArrayDeque",
          "java.lang.StringBuilder");

  /** Owner.field of a cache, and what rebuilds it from state.json. */
  static final Map<String, String> CACHES = Map.of();

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(offenders().toList());
  }

  static Stream<String> offenders() {
    return SourceRoots.ALL.stream()
        .filter(CollaboratorOwners.areMainClasses())
        .filter(type -> type.isMetaAnnotatedWith(Component.class))
        .filter(InstanceStateFitness::inComponentPackage)
        .flatMap(type -> type.getFields().stream())
        .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
        .filter(field ->
            isMutable(field) && (!isFinalInterface(field) || buildsHolder(field.getOwner())))
        .filter(field -> !CACHES.containsKey(name(field)))
        .map(InstanceStateFitness::name);
  }

  private static String name(JavaField field) {
    return field.getOwner().getSimpleName() + "." + field.getName();
  }

  private static boolean inComponentPackage(JavaClass type) {
    return COMPONENT_PACKAGES.stream().anyMatch(base -> type.getPackageName().startsWith(base));
  }

  private static boolean isMutable(JavaField field) {
    return !field.getModifiers().contains(JavaModifier.FINAL) || isHolder(field.getRawType());
  }

  private static boolean isHolder(JavaClass type) {
    String name = type.getName();
    return name.startsWith("[") || HOLDERS.stream().anyMatch(name::startsWith);
  }

  private static boolean isFinalInterface(JavaField field) {
    return field.getModifiers().contains(JavaModifier.FINAL) && field.getRawType().isInterface();
  }

  private static boolean buildsHolder(JavaClass owner) {
    return owner.getConstructorCallsFromSelf().stream()
        .map(call -> call.getTargetOwner())
        .anyMatch(target -> !target.isInterface() && isHolder(target));
  }
}
