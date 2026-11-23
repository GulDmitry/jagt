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
    write("scripts/a.sh", "#!/bin/sh\n# exits: 0 2\nexit 0\n");
    write(TESTS + "scripts.list", "scripts/a.sh\n");
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
        .contains("no case: scripts/a.sh has no case in scripts/guards/test/a.bats");
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
    write("scripts/a.sh", "#!/bin/sh\nexit 0\n");

    assertThat(GuardsGate.problems(tree))
        .contains("no contract: scripts/a.sh has no '# exits:' header line");
  }

  @Test
  void optionKeyNeedsItsTaggedCaseToo() throws IOException {
    write("scripts/a.sh", "#!/bin/sh\n# exits: 0 2\n# options: --force\nexit 0\n");

    assertThat(GuardsGate.problems(tree))
        .containsExactly(
            "untested contract: a.sh opt:--force has no case tagged for "
                + "it in scripts/guards/test/a.bats");
  }

  @Test
  void listEntryNamingNoFileIsRed() throws IOException {
    write(TESTS + "scripts.list", "scripts/gone.sh\n");

    assertThat(GuardsGate.problems(tree)).contains("missing script: scripts/gone.sh names no file");
  }

  @Test
  void globEntryListsEveryMatchingScript() throws IOException {
    write(TESTS + "scripts.list", "scripts/*.sh\n");
    write("scripts/b.sh", "#!/bin/sh\n# exits: 0\nexit 0\n");

    assertThat(GuardsGate.scripts(tree))
        .containsExactly(tree.resolve("scripts/a.sh"), tree.resolve("scripts/b.sh"));
  }

  @Test
  void missingListIsRed() throws IOException {
    Files.delete(tree.resolve(TESTS + "scripts.list"));

    assertThat(GuardsGate.problems(tree))
        .containsExactly("no list: scripts/guards/test/scripts.list is missing");
  }

  private void write(String path, String content) throws IOException {
    Path file = tree.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
