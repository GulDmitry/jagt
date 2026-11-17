package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.Test;

class CollaboratorOwnersTest {

  @Test
  void mainClassBuildsNoCollaboratorOfItsOwn() {
    classes()
        .that(CollaboratorOwners.areMainClasses())
        .should(CollaboratorOwners.buildNoCollaboratorOfTheirOwn())
        .because(
            "a collaborator arrives through the constructor; only one made of what the class holds,"
                + " a static factory, a @Bean method or a post-processor builds its own")
        .check(SourceRoots.ALL);
  }
}
