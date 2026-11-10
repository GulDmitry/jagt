package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

class LifecycleHooksTest {

  @Test
  void noMethodRunsWorkAsAPostConstructHook() {
    FreezingArchRule.freeze(
            noMethods()
                .that()
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("AbortedConnectionFilter")
                .should()
                .beAnnotatedWith("jakarta.annotation.PostConstruct"))
        .check(MainClasses.CODE);
  }

  @Test
  void noFieldIsAutowired() {
    FreezingArchRule.freeze(
            fields()
                .should()
                .notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired"))
        .check(MainClasses.CODE);
  }
}
