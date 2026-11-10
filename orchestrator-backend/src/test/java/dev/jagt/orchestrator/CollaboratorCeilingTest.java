package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

import com.tngtech.archunit.base.DescribedPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

class CollaboratorCeilingTest {

  static final int CEILING = 5;

  @Test
  void componentTakesFiveCollaboratorsAtMost() {
    constructors()
        .that()
        .areDeclaredInClassesThat()
        .areMetaAnnotatedWith(Component.class)
        .should()
        .haveRawParameterTypes(
            DescribedPredicate.describe("the ceiling at most", types -> types.size() <= CEILING))
        .check(SourceRoots.ALL);
  }
}
