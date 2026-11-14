package dev.jagt.orchestrator;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class TestKinds {

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
  private static final String CLASSES = "/build/classes/java/";
  private static final String COMPONENT = "org.springframework.stereotype.Component";
  private static final Set<String> OUTSIDE_VALUES =
      Set.of(
          "java.time.Clock.systemUTC",
          "java.time.Clock.systemDefaultZone",
          "java.time.Instant.now",
          "java.lang.System.currentTimeMillis",
          "java.lang.System.nanoTime",
          "java.lang.Math.random",
          "java.util.UUID.randomUUID");
  private static final Pattern BRANCH_OR_LOOP =
      Pattern.compile("\\b(if|for|while|switch)\\s*\\(|\\bdo\\s*\\{|\\s\\?\\s");
  private static final String ROOT_TEST_SET = "orchestrator-backend/build/classes/java/test/";

  private TestKinds() {}

  static DescribedPredicate<JavaClass> declaredInTestClass() {
    return DescribedPredicate.describe(
        "a test class", type -> outermost(type).getSimpleName().endsWith("Test"));
  }

  private static JavaClass outermost(JavaClass type) {
    return type.getEnclosingClass().map(TestKinds::outermost).orElse(type);
  }

  static ArchCondition<JavaMethod> beTestSetupOrArguments() {
    return new ArchCondition<>("be a test, its setup or its arguments") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (!isTestSetupOrArguments(method)) {
          events.add(SimpleConditionEvent.violated(method, method.getFullName()));
        }
      }
    };
  }

  private static boolean isTestSetupOrArguments(JavaMethod method) {
    return method.getModifiers().contains(JavaModifier.SYNTHETIC)
        || method.isMetaAnnotatedWith(TESTABLE)
        || method.getAnnotations().stream()
            .anyMatch(a -> CALLED_BY_A_FRAMEWORK.contains(a.getRawType().getName()))
        || MainMembers.overrides(method)
        || argumentsOf(outermost(method.getOwner())).contains(method.getName());
  }

  private static Set<String> argumentsOf(JavaClass testClass) {
    return SourceRoots.ALL.stream()
        .filter(type -> outermost(type).equals(testClass))
        .flatMap(type -> type.getMethods().stream())
        .flatMap(TestKinds::sourcesNamedBy)
        .collect(Collectors.toSet());
  }

  private static Stream<String> sourcesNamedBy(JavaMethod test) {
    return test.tryGetAnnotationOfType(METHOD_SOURCE)
        .map(source -> (String[]) source.get("value").orElse(new String[0]))
        .map(names -> Arrays.stream(names).map(name -> name.isEmpty() ? test.getName() : name))
        .map(stream -> stream.map(name -> name.substring(name.indexOf('#') + 1)))
        .orElseGet(Stream::empty);
  }

  static DescribedPredicate<JavaClass> areTopLevelInTestSet() {
    return DescribedPredicate.describe(
        "top-level in a test source set",
        type ->
            type.isTopLevelClass()
                && !type.getSimpleName().equals("package-info")
                && !location(type).contains(CLASSES + "main/"));
  }

  static String location(JavaClass type) {
    return type.getSource().map(source -> source.getUri().toString()).orElse("");
  }

  static ArchCondition<JavaClass> beTestBuilderFakeOrScanner() {
    return new ArchCondition<>("be a test, a Test<Thing>Builder record, a fake or a scanner") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        if (!isTestBuilderFakeOrScanner(type)) {
          events.add(SimpleConditionEvent.violated(type, type.getName()));
        }
      }
    };
  }

  private static boolean isTestBuilderFakeOrScanner(JavaClass type) {
    String name = type.getSimpleName();
    return name.endsWith("Test")
        || (name.startsWith("Test") && name.endsWith("Builder") && type.isRecord())
        || (name.startsWith("Fake") && extendsMainType(type))
        || location(type).contains(ROOT_TEST_SET);
  }

  private static boolean extendsMainType(JavaClass type) {
    return Stream.concat(type.getAllRawSuperclasses().stream(), type.getAllRawInterfaces().stream())
        .anyMatch(parent ->
            location(parent).contains(CLASSES + "main/")
                || !parent.getPackageName().startsWith("dev.jagt"));
  }

  static DescribedPredicate<JavaClass> testBuilders() {
    return DescribedPredicate.describe(
        "Test<Thing>Builders",
        type ->
            type.getSimpleName().startsWith("Test") && type.getSimpleName().endsWith("Builder"));
  }

  static DescribedPredicate<JavaMethodCall> readClockRandomMockOrAssertion() {
    return DescribedPredicate.describe(
        "a clock, random, mock or assertion call",
        call -> {
          String owner = call.getTargetOwner().getName();
          String name = call.getName();
          return OUTSIDE_VALUES.contains(owner + "." + name)
              || owner.startsWith("org.mockito.")
              || owner.startsWith("org.assertj.")
              || owner.equals("org.junit.jupiter.api.Assertions")
              || owner.equals("java.util.Random")
              || owner.equals("java.util.concurrent.ThreadLocalRandom");
        });
  }

  static Stream<Path> testBuilderSources() {
    return SourceRoots.javaFiles()
        .filter(path -> path.getFileName().toString().matches("Test\\w*Builder\\.java"));
  }

  static boolean holdsBranchOrLoop(Path source) {
    return BRANCH_OR_LOOP.matcher(SourceRoots.read(source)).find();
  }

  static ArchCondition<JavaClass> wireOneComponentAtMost() {
    return new ArchCondition<>("construct one Spring component at most") {
      @Override
      public void check(JavaClass builder, ConditionEvents events) {
        long components =
            builder.getConstructorCallsFromSelf().stream()
                .map(call -> call.getTargetOwner())
                .filter(type -> type.isMetaAnnotatedWith(COMPONENT))
                .distinct()
                .count();
        if (components > 1) {
          events.add(SimpleConditionEvent.violated(builder, builder.getName()));
        }
      }
    };
  }

  static DescribedPredicate<JavaClass> moduleUnitTests() {
    return DescribedPredicate.describe(
        "unit tests of a module",
        type ->
            type.isTopLevelClass()
                && type.getSimpleName().endsWith("Test")
                && location(type).contains(CLASSES + "test/")
                && !location(type).contains(ROOT_TEST_SET));
  }

  static ArchCondition<JavaClass> beNamedAfterMainClassOfItsPackage() {
    return new ArchCondition<>("be named after a main class of its package") {
      @Override
      public void check(JavaClass test, ConditionEvents events) {
        String tested = test.getSimpleName().replaceFirst("Test$", "");
        boolean named =
            SourceRoots.ALL.stream()
                .filter(JavaClass::isTopLevelClass)
                .filter(type -> !location(type).contains(CLASSES + "test/"))
                .filter(type -> type.getPackageName().equals(test.getPackageName()))
                .anyMatch(type -> tested.startsWith(type.getSimpleName()));
        if (!named) {
          events.add(SimpleConditionEvent.violated(test, test.getName()));
        }
      }
    };
  }
}
