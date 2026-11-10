package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaticStateTest {

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

  @Test
  void staticFieldIsFinalAndOfAnImmutableType() {
    FreezingArchRule.freeze(
            fields()
                .that()
                .areStatic()
                .and()
                .doNotHaveModifier(JavaModifier.SYNTHETIC)
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("TaskAliasLogFilter")
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("AbortedConnectionFilter")
                .should(beFinalAndImmutable()))
        .check(MainClasses.CODE);
  }

  private static ArchCondition<JavaField> beFinalAndImmutable() {
    return new ArchCondition<>("be final and of an immutable type") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        String type = field.getRawType().getName();
        boolean mutable =
            type.startsWith("[") || MUTABLE_PREFIXES.stream().anyMatch(type::startsWith);
        if (!field.getModifiers().contains(JavaModifier.FINAL) || mutable) {
          events.add(SimpleConditionEvent.violated(field, field.getDescription()));
        }
      }
    };
  }
}
