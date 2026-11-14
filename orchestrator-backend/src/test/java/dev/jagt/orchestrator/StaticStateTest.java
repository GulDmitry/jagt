package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.core.domain.JavaModifier;
import org.junit.jupiter.api.Test;

class StaticStateTest {

  @Test
  void staticFieldIsFinalAndOfAnImmutableType() {
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
        .should(ImmutableStatics.beFinalAndImmutable())
        .check(SourceRoots.ALL);
  }
}
