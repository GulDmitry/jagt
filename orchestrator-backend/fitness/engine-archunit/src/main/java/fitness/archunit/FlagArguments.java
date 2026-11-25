package fitness.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import fitness.core.Measurement;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class FlagArguments {

  private static final List<String> BOOLEANS = List.of("boolean", "java.lang.Boolean");

  private FlagArguments() {}

  static Measurement measure(List<JavaClass> owners, String stereotype) {
    return Measurement.of(
        owners.stream()
            .filter(type -> !type.isNestedClass())
            .filter(type -> isPort(type) || type.isMetaAnnotatedWith(stereotype))
            .flatMap(type -> type.getMethods().stream())
            .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
            .flatMap(FlagArguments::flags)
            .sorted()
            .toList());
  }

  private static boolean isPort(JavaClass type) {
    return type.isInterface()
        && (type.getPackageName().contains(".port.") || type.getPackageName().endsWith(".port"));
  }

  private static Stream<String> flags(JavaMethod method) {
    List<String> types = method.getRawParameterTypes().stream().map(JavaClass::getName).toList();
    return IntStream.range(0, types.size())
        .filter(index -> BOOLEANS.contains(types.get(index)))
        .mapToObj(
            index -> method.getOwner().getName() + "#" + method.getName() + "(" + index + ")");
  }
}
