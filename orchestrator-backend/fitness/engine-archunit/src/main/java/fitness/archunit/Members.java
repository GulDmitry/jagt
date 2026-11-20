package fitness.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Lists the members and calls of the selected classes that a function's selectors name. */
final class Members {

  private static final String ANNOTATED = "annotated";
  private static final String WILDCARD = ".*";
  private static final List<String> MUTABLE_TYPES =
      List.of(
          "java.util.concurrent.atomic.",
          "java.util.concurrent.ConcurrentHashMap",
          "java.util.concurrent.CopyOnWrite",
          "java.util.HashMap",
          "java.util.LinkedHashMap",
          "java.util.TreeMap",
          "java.util.ArrayList",
          "java.util.LinkedList",
          "java.util.HashSet",
          "java.util.ArrayDeque",
          "java.lang.StringBuilder");
  private static final String INJECTED = "org.junit.jupiter.api.io.TempDir";

  private Members() {}

  static List<String> fields(List<JavaClass> owners, Map<String, Object> selectors) {
    boolean statics = Boolean.TRUE.equals(selectors.get("static"));
    boolean mutable = Boolean.TRUE.equals(selectors.get("mutable"));
    Object annotation = selectors.get(ANNOTATED);
    return owners.stream()
        .flatMap(type -> type.getFields().stream())
        .filter(field -> !field.getModifiers().contains(JavaModifier.SYNTHETIC))
        .filter(field -> !statics || field.getModifiers().contains(JavaModifier.STATIC))
        .filter(field -> annotation == null || field.isMetaAnnotatedWith(annotation.toString()))
        .filter(field -> !mutable || isMutable(field))
        .map(JavaField::getDescription)
        .toList();
  }

  static List<String> methods(List<JavaClass> owners, Map<String, Object> selectors) {
    Object annotation = selectors.get(ANNOTATED);
    return owners.stream()
        .flatMap(type -> type.getMethods().stream())
        .filter(method -> annotation == null || method.isMetaAnnotatedWith(annotation.toString()))
        .map(JavaMethod::getFullName)
        .toList();
  }

  static List<String> calls(List<JavaClass> owners, String call, Object without) {
    int dot = call.lastIndexOf('.');
    String target = call.substring(0, dot);
    String name = call.substring(dot + 1);
    return owners.stream()
        .flatMap(type -> type.getMethodCallsFromSelf().stream())
        .filter(found -> found.getName().equals(name) && ownedBy(found, target))
        .filter(found -> without == null || !takes(found, Objects.toString(without)))
        .map(JavaMethodCall::getDescription)
        .toList();
  }

  private static boolean ownedBy(JavaMethodCall call, String target) {
    JavaClass owner = call.getTargetOwner();
    return target.endsWith(WILDCARD)
        ? owner.getPackageName().equals(target.substring(0, target.length() - WILDCARD.length()))
        : owner.getName().equals(target);
  }

  private static boolean takes(JavaMethodCall call, String type) {
    return call.getTarget().getRawParameterTypes().stream()
        .anyMatch(parameter -> parameter.getName().equals(type));
  }

  private static boolean isMutable(JavaField field) {
    String type = field.getRawType().getName();
    boolean mutableType = type.startsWith("[") || MUTABLE_TYPES.stream().anyMatch(type::startsWith);
    return !field.isAnnotatedWith(INJECTED)
        && (!field.getModifiers().contains(JavaModifier.FINAL) || mutableType);
  }
}
