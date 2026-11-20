package dev.jagt.orchestrator;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class MainMembers {

  private static final Set<String> CALLED_BY_NAME = Set.of("main", "values", "valueOf");
  private static final List<String> FRAMEWORKS =
      List.of(
          "org.springframework.",
          "com.fasterxml.",
          "tools.jackson.",
          "jakarta.",
          "io.modelcontextprotocol.",
          "org.springaicommunity.");

  private MainMembers() {}

  public static DescribedPredicate<JavaMethod> calledOnlyFromOutsideMain() {
    return DescribedPredicate.describe(
        "are never called from main nor by a framework",
        method ->
            !method.getModifiers().contains(JavaModifier.PRIVATE)
                && !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                && !method.getModifiers().contains(JavaModifier.BRIDGE)
                && method.getAccessesToSelf().isEmpty()
                && !CALLED_BY_NAME.contains(method.getName())
                && !frameworkCalls(method)
                && !overrides(method)
                && !accessor(method));
  }

  private static boolean frameworkCalls(JavaMethod method) {
    return method.getAnnotations().stream()
        .map(annotation -> annotation.getRawType().getName())
        .anyMatch(name -> FRAMEWORKS.stream().anyMatch(name::startsWith));
  }

  static boolean overrides(JavaMethod method) {
    List<JavaClass> parameters = method.getRawParameterTypes();
    return Stream.concat(
            method.getOwner().getAllRawSuperclasses().stream(),
            method.getOwner().getAllRawInterfaces().stream())
        .flatMap(type -> type.getMethods().stream())
        .anyMatch(other ->
            other.getName().equals(method.getName())
                && other.getRawParameterTypes().equals(parameters));
  }

  private static boolean accessor(JavaMethod method) {
    String name = method.getName();
    boolean component =
        method.getOwner().isRecord()
            && method.getRawParameterTypes().isEmpty()
            && method.getOwner().tryGetField(name).isPresent();
    boolean getter =
        method.getRawParameterTypes().isEmpty()
            && (name.startsWith("get") || name.startsWith("is"));
    return component || getter;
  }
}
