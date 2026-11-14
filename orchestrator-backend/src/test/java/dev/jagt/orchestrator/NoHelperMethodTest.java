package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NoHelperMethodTest {

  private static final String TESTABLE = "org.junit.platform.commons.annotation.Testable";
  private static final String METHOD_SOURCE = "org.junit.jupiter.params.provider.MethodSource";
  private static final Set<String> CALLED_BY_A_FRAMEWORK =
      Set.of(
          "org.junit.jupiter.api.BeforeEach",
          "org.junit.jupiter.api.AfterEach",
          "org.junit.jupiter.api.BeforeAll",
          "org.junit.jupiter.api.AfterAll",
          "org.springframework.context.annotation.Bean",
          "org.springframework.test.context.DynamicPropertySource");

  @Test
  void testClassDeclaresNoMethodButTestsSetupAndArguments() {
    FreezingArchRule.freeze(
            methods()
                .that()
                .areDeclaredInClassesThat(declaredInTestClass())
                .should(beTestSetupOrArguments()))
        .check(SourceRoots.ALL);
  }

  private static DescribedPredicate<JavaClass> declaredInTestClass() {
    return DescribedPredicate.describe(
        "a test class", type -> outermost(type).getSimpleName().endsWith("Test"));
  }

  private static JavaClass outermost(JavaClass type) {
    return type.getEnclosingClass().map(NoHelperMethodTest::outermost).orElse(type);
  }

  private static ArchCondition<JavaMethod> beTestSetupOrArguments() {
    return new ArchCondition<>("be a test, its setup or its arguments") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (!allowed(method)) {
          events.add(SimpleConditionEvent.violated(method, method.getFullName()));
        }
      }
    };
  }

  private static boolean allowed(JavaMethod method) {
    return method.getModifiers().contains(JavaModifier.SYNTHETIC)
        || method.isMetaAnnotatedWith(TESTABLE)
        || method.getAnnotations().stream()
            .anyMatch(a -> CALLED_BY_A_FRAMEWORK.contains(a.getRawType().getName()))
        || overrides(method)
        || argumentsOf(outermost(method.getOwner())).contains(method.getName());
  }

  private static boolean overrides(JavaMethod method) {
    List<JavaClass> parameters = method.getRawParameterTypes();
    return Stream.concat(
            method.getOwner().getAllRawSuperclasses().stream(),
            method.getOwner().getAllRawInterfaces().stream())
        .flatMap(type -> type.getMethods().stream())
        .anyMatch(
            other ->
                other.getName().equals(method.getName())
                    && other.getRawParameterTypes().equals(parameters));
  }

  private static Set<String> argumentsOf(JavaClass testClass) {
    return SourceRoots.ALL.stream()
        .filter(type -> outermost(type).equals(testClass))
        .flatMap(type -> type.getMethods().stream())
        .flatMap(NoHelperMethodTest::sourcesNamedBy)
        .collect(Collectors.toSet());
  }

  private static Stream<String> sourcesNamedBy(JavaMethod test) {
    return test.tryGetAnnotationOfType(METHOD_SOURCE)
        .map(source -> (String[]) source.get("value").orElse(new String[0]))
        .map(names -> names.length == 0 ? Stream.of(test.getName()) : Arrays.stream(names))
        .map(stream -> stream.map(name -> name.substring(name.indexOf('#') + 1)))
        .orElseGet(Stream::empty);
  }
}
