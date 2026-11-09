package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleCasesTest {

  @Test
  void localCasesAreReadAfterTheShippedOnes(@TempDir Path role) throws Exception {
    Files.createDirectories(role.resolve("eval"));
    Files.createDirectories(role.resolve("eval.local"));
    Files.writeString(role.resolve("eval/01-shipped.yml"), "shipped");
    Files.writeString(role.resolve("eval.local/01-local.yml"), "local");
    Files.writeString(role.resolve("eval.local/notes.txt"), "ignored");

    assertThat(RoleCases.in(role, text -> text)).containsExactly("shipped", "local");
  }

  @Test
  void aRoleWithoutLocalCasesReadsOnlyTheShippedOnes(@TempDir Path role) throws Exception {
    Files.createDirectories(role.resolve("eval"));
    Files.writeString(role.resolve("eval/01-shipped.yml"), "shipped");

    assertThat(RoleCases.in(role, text -> text)).containsExactly("shipped");
  }
}
