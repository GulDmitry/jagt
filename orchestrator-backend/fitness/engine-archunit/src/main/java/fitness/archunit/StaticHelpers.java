package fitness.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import fitness.core.Measurement;
import java.util.List;

final class StaticHelpers {

  private StaticHelpers() {}

  static Measurement measure(List<JavaClass> owners, String stereotype) {
    return Measurement.of(
        owners.stream()
            .filter(type -> !type.isNestedClass())
            .filter(type -> type.isMetaAnnotatedWith(stereotype))
            .flatMap(type -> type.getMethods().stream())
            .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
            .filter(method -> method.getModifiers().contains(JavaModifier.STATIC))
            .map(JavaMethod::getFullName)
            .sorted()
            .toList());
  }
}
