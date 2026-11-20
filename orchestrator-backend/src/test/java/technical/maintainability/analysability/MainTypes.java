package technical.maintainability.analysability;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import dev.jagt.orchestrator.MainClasses;
import java.util.stream.Stream;

final class MainTypes {

  private MainTypes() {}

  static Stream<JavaClass> concrete(String supertype) {
    return MainClasses.CODE.stream()
        .filter(type -> type.isAssignableTo(supertype) && !type.getName().equals(supertype))
        .filter(
            type -> !type.isInterface() && !type.getModifiers().contains(JavaModifier.ABSTRACT));
  }

  static Stream<JavaMethod> methods() {
    return MainClasses.CODE.stream().flatMap(type -> type.getMethods().stream());
  }
}
