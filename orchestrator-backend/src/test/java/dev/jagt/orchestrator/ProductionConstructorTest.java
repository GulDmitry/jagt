package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

class ProductionConstructorTest {

  @Test
  void everyConstructorIsCalledFromMain() {
    constructors()
        .that()
        .areNotPrivate()
        .and(ConstructorOwners.notCalledFromMain())
        .should(ArchCondition.from(ConstructorOwners.springOrRecordOwns()))
        .check(MainClasses.CODE);
  }

  @Test
  void everyMemberIsCalledFromMain() {
    FreezingArchRule.freeze(
            methods()
                .that(MainMembers.calledOnlyFromOutsideMain())
                .should(
                    ArchCondition.from(
                        DescribedPredicate.describe("be called from main", method -> false))))
        .check(MainClasses.CODE);
  }
}
