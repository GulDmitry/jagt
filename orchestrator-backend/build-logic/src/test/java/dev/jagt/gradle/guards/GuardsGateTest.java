package dev.jagt.gradle.guards;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuardsGateTest {

  private static final String CASES =
      "# bats test_tags=exit:0\n@test \"passes\" { true; }\n\n"
          + "# bats test_tags=exit:2\n@test \"refuses\" { true; }\n";
  private static final String EXIT_0_ONLY = "# bats test_tags=exit:0\n@test \"passes\" { true; }\n";
  private static final String TESTS = "scripts/guards/test/";

  @TempDir Path tree;

  @BeforeEach
  void greenTree() throws IOException {
    write("scripts/guards/a.sh", "#!/bin/sh\n# exits: 0 2\nexit 0\n");
    write("fitness.yml", exceptList("../scripts/guards/*.sh"));
    write(TESTS + "a.bats", CASES);
  }

  @Test
  void treeWithCaseFileAndTaggedCasePerKeyIsGreen() {
    assertThat(GuardsGate.problems(tree)).isEmpty();
  }

  @Test
  void listedScriptWithoutCaseFileIsRed() throws IOException {
    Files.delete(tree.resolve(TESTS + "a.bats"));

    assertThat(GuardsGate.problems(tree))
        .contains("no case: scripts/guards/a.sh has no case in scripts/guards/test/a.bats");
  }

  @Test
  void contractKeyWithoutTaggedCaseOrGapIsRed() throws IOException {
    write(TESTS + "a.bats", EXIT_0_ONLY);

    assertThat(GuardsGate.problems(tree))
        .containsExactly(
            "untested contract: a.sh exit:2 has no case tagged for "
                + "it in scripts/guards/test/a.bats");
  }

  @Test
  void contractKeyListedAsGapIsGreen() throws IOException {
    write(TESTS + "a.bats", EXIT_0_ONLY);
    write(TESTS + "contract-gaps.txt", "a.sh exit:2\n");

    assertThat(GuardsGate.problems(tree)).isEmpty();
  }

  @Test
  void gapWhoseKeyHasTaggedCaseIsStale() throws IOException {
    write(TESTS + "contract-gaps.txt", "a.sh exit:2\n");

    assertThat(GuardsGate.problems(tree))
        .containsExactly(
            "stale gap: 'a.sh exit:2' in "
                + "scripts/guards/test/contract-gaps.txt has a tagged case, delete the line");
  }

  @Test
  void gapForKeyNoLongerStatedIsStale() throws IOException {
    write(TESTS + "contract-gaps.txt", "a.sh exit:9\n");

    assertThat(GuardsGate.problems(tree))
        .containsExactly(
            "stale gap: 'a.sh exit:9' in "
                + "scripts/guards/test/contract-gaps.txt is not a contract key, delete the line");
  }

  @Test
  void scriptWithoutContractHeaderIsRed() throws IOException {
    write("scripts/guards/a.sh", "#!/bin/sh\nexit 0\n");

    assertThat(GuardsGate.problems(tree))
        .contains("no contract: scripts/guards/a.sh has no '# exits:' header line");
  }

  @Test
  void optionKeyNeedsItsTaggedCaseToo() throws IOException {
    write("scripts/guards/a.sh", "#!/bin/sh\n# exits: 0 2\n# options: --force\nexit 0\n");

    assertThat(GuardsGate.problems(tree))
        .containsExactly(
            "untested contract: a.sh opt:--force has no case tagged for "
                + "it in scripts/guards/test/a.bats");
  }

  @Test
  void exceptEntryNamingNoTrackedFileIsRed() throws IOException {
    write("fitness.yml", exceptList("../scripts/guards/*.sh,gradle/gone.sh"));

    assertThat(GuardsGate.problems(tree)).contains("missing script: gradle/gone.sh names no file");
  }

  @Test
  void globEntryListsEveryMatchingShellScript() throws IOException {
    write("scripts/guards/b.sh", "#!/bin/sh\n# exits: 0\nexit 0\n");

    assertThat(GuardsGate.scripts(tree))
        .containsExactly(
            tree.resolve("scripts/guards/a.sh"), tree.resolve("scripts/guards/b.sh"));
  }

  @Test
  void shellEntryJoiningExceptListIsLintedWithoutAnotherList() throws IOException {
    write("orchestrator-backend/scripts/c.sh", "#!/bin/sh\nexit 0\n");
    write("fitness.yml", exceptList("../scripts/guards/*.sh,scripts/c.sh"));

    assertThat(GuardsGate.scripts(tree))
        .contains(tree.resolve("orchestrator-backend/scripts/c.sh"));
  }

  @Test
  void nonShellEntryIsNotLinted() throws IOException {
    write("mcp_client.js", "x\n");
    write("fitness.yml", exceptList("../scripts/guards/*.sh,../mcp_client.js"));

    assertThat(GuardsGate.scripts(tree)).containsExactly(tree.resolve("scripts/guards/a.sh"));
  }

  @Test
  void scriptOutsideGuardsWithoutCaseFileNeedsNoContract() throws IOException {
    write("orchestrator-backend/scripts/c.sh", "#!/bin/sh\nexit 0\n");
    write("fitness.yml", exceptList("../scripts/guards/*.sh,scripts/c.sh"));

    assertThat(GuardsGate.problems(tree)).isEmpty();
  }

  @Test
  void treeWithoutExceptListIsRed() throws IOException {
    write("fitness.yml", "rules: {}\n");

    assertThat(GuardsGate.problems(tree))
        .containsExactly("no list: fitness.yml has no scripts-are-java except list");
  }

  private static String exceptList(String entries) {
    return "      scripts-are-java: { engine: scan, per: file, except: \"" + entries
        + "\", governs: count, max: 0 }\n";
  }

  private void write(String path, String content) throws IOException {
    Path file = tree.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
