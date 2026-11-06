package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectBlocksTest {

    private final ProjectSetup.Entry entry = new ProjectSetup.Entry("billing", "/p", "origin/main", null, null, null, null, null);

    @Test
    void writesTheManagedKeysAtTheIndentTheBlockAlreadyUses() {
        String file = """
                orchestrator:
                  projects:
                    billing:
                            path: /old
                            codeHost: github
                """;

        String edited = ProjectBlocks.replaced(file, entry, Path.of("/p"));

        assertThat(edited).isEqualTo("""
                orchestrator:
                  projects:
                    billing:
                            path: "/p"
                            baseBranch: "origin/main"
                            codeHost: github
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
                      codeHost: github
                """;

        String edited = ProjectBlocks.replaced(file, entry, Path.of("/p"));

        assertThat(edited).isEqualTo("""
                orchestrator:
                  projects:
                    billing:
                      path: "/p"
                      baseBranch: "origin/main"
                      codeHost: github
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
                      codeHost: github
                """;

        String edited = ProjectBlocks.replaced(file, entry, Path.of("/p"));

        assertThat(edited).isEqualTo("""
                orchestrator:
                  projects:
                    billing:
                      path: "/p"
                      baseBranch: "origin/main"
                      # why
                      codeHost: github
                """);
    }

    @Test
    void writesTheLabelsAndHumanPathsOfAnInsertedEntryAfterItsOtherKeys() {
        String file = """
                orchestrator:
                  projects: {}
                """;
        ProjectSetup.Entry typed = new ProjectSetup.Entry("billing", "/p", "origin/main", null, null, null,
                List.of("bill", "pay"), List.of("**/auth/**"));

        String edited = ProjectBlocks.inserted(file, typed, Path.of("/p"));

        assertThat(edited).isEqualTo("""
                orchestrator:
                  projects:
                    billing:
                      path: "/p"
                      baseBranch: "origin/main"
                      labels: ["bill","pay"]
                      humanPaths: ["**/auth/**"]
                """);
    }

    @Test
    void rewritesTheLabelsOfABlockAndKeepsItsCodeHostAndComment() {
        String file = """
                orchestrator:
                  projects:
                    billing:
                      path: /old
                      labels: [old]
                      # why
                      codeHost: github
                """;
        ProjectSetup.Entry typed = new ProjectSetup.Entry("billing", "/p", "origin/main", null, null, null,
                List.of("bill"), null);

        String edited = ProjectBlocks.replaced(file, typed, Path.of("/p"));

        assertThat(edited).isEqualTo("""
                orchestrator:
                  projects:
                    billing:
                      path: "/p"
                      baseBranch: "origin/main"
                      labels: ["bill"]
                      # why
                      codeHost: github
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
