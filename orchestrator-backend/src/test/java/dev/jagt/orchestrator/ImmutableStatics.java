package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;

final class ImmutableStatics {

  private static final List<String> MUTABLE_PREFIXES =
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

  private ImmutableStatics() {}

  static ArchCondition<JavaField> beFinalAndImmutable() {
    return new ArchCondition<>("be final and of an immutable type") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        String type = field.getRawType().getName();
        boolean mutable =
            type.startsWith("[") || MUTABLE_PREFIXES.stream().anyMatch(type::startsWith);
        boolean injectedByJunit = field.isAnnotatedWith("org.junit.jupiter.api.io.TempDir");
        if (!injectedByJunit && (!field.getModifiers().contains(JavaModifier.FINAL) || mutable)) {
          events.add(SimpleConditionEvent.violated(field, field.getDescription()));
        }
      }
    };
  }
}
