package fitness.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import fitness.core.Measurement;
import java.util.List;
import java.util.Set;

final class SingleImplementers {

  private SingleImplementers() {}

  static Measurement measure(List<JavaClass> interfaces) {
    return Measurement.of(
        interfaces.stream()
            .filter(type -> type.isInterface() && !type.isAnnotation() && !type.isNestedClass())
            .filter(SingleImplementers::hasOneImplementerBeside)
            .map(JavaClass::getName)
            .toList());
  }

  private static boolean hasOneImplementerBeside(JavaClass type) {
    Set<JavaClass> implementers = type.getSubclasses();
    return implementers.size() == 1
        && implementers.stream()
            .allMatch(
                only ->
                    !only.isInterface() && only.getPackageName().equals(type.getPackageName()));
  }
}
