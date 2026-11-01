package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectBlocksTest {

    private final ProjectSetup.Entry entry = new ProjectSetup.Entry("billing", "/p", "origin/main", null, null, null);

    @Test
    void writesTheManagedKeysAtTheIndentTheBlockAlreadyUses() {
        String file = """
                orchestrator:
                  projects:
                    billing:
                            path: /old
                            labels: [B]
                """;

        String edited = ProjectBlocks.replaced(file, entry, Path.of("/p"));

        assertThat(edited).isEqualTo("""
                orchestrator:
                  projects:
                    billing:
                            path: "/p"
                            baseBranch: "origin/main"
                            labels: [B]
                """);
    }

    @Test
    void writesTheManagedKeysOneStepDeeperWhereTheBlockHoldsNoLine() {
        String file = """
                orchestrator:
                  projects:
                    billing:
                    web:
                      path: /srv/web
                """;

        String edited = ProjectBlocks.replaced(file, entry, Path.of("/p"));

        assertThat(edited).isEqualTo("""
                orchestrator:
                  projects:
                    billing:
                      path: "/p"
                      baseBranch: "origin/main"
                    web:
                      path: /srv/web
                """);
    }

    @Test
    void dropsAManagedListWhoseDashesSitAtTheIndentOfItsKey() {
        String file = """
                orchestrator:
                  projects:
                    billing:
                      path: /old
                      verifyCommand:
                      - make
                      - test
                      labels: [B]
                """;

        String edited = ProjectBlocks.replaced(file, entry, Path.of("/p"));

        assertThat(edited).isEqualTo("""
                orchestrator:
                  projects:
                    billing:
                      path: "/p"
                      baseBranch: "origin/main"
                      labels: [B]
                """);
    }

    @Test
    void keepsTheCommentThatFollowsAManagedKey() {
        String file = """
                orchestrator:
                  projects:
                    billing:
                      path: /old
                      # why
                      labels: [B]
                """;

        String edited = ProjectBlocks.replaced(file, entry, Path.of("/p"));

        assertThat(edited).isEqualTo("""
                orchestrator:
                  projects:
                    billing:
                      path: "/p"
                      baseBranch: "origin/main"
                      # why
                      labels: [B]
                """);
    }

    @Test
    void holdsNoBlockForAProjectKeyUnderAnotherSectionOfOrchestrator() {
        String file = """
                orchestrator:
                  projects:
                    web:
                      path: /srv/web
                  other:
                    billing:
                      path: /srv/billing
                """;

        assertThatThrownBy(() -> ProjectBlocks.removed(file, "billing")).isInstanceOf(IllegalArgumentException.class);
    }
}
