package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceWorkspaceFilesTest {

  private static final String CASE =
      """
      case: seeds
      ticket:
        key: ABC-1
        title: A title
        criteria:
          - {id: C1, text: "first"}
      deploy: {sha: 1829304, landed: host-job, diff: ["a/B.java: change"]}
      workspace:
        index.md: "# index"
      expect:
        rows: {C1: met}
        report: plain
        ending: passed
        writes: %s
      names: []
      """;

  @Test
  void passesAWorkspaceThatGotANewFlowAndRanTheFlowsCommandOnTheEnvironment(@TempDir Path dir)
      throws Exception {
    AcceptanceCase expected = AcceptanceCases.parse(CASE.formatted("new"));
    AcceptanceWorkspaceFiles.seed(dir, expected);
    Set<String> before = AcceptanceWorkspaceFiles.snapshot(dir);
    Files.createDirectories(dir.resolve("flows"));
    Files.writeString(dir.resolve("flows/currency.test.mjs"), "test");
    Files.writeString(
        dir.resolve("runs.log"), "http://127.0.0.1:9 flows/currency.test.mjs exit=0\n");

    String problem = AcceptanceWorkspaceFiles.problem(expected, before, dir, "http://127.0.0.1:9");

    assertThat(problem).isEmpty();
  }

  @Test
  void namesTheFileAWorkspaceGotWhereNothingNewWasExpected(@TempDir Path dir) throws Exception {
    AcceptanceCase expected = AcceptanceCases.parse(CASE.formatted("none"));
    AcceptanceWorkspaceFiles.seed(dir, expected);
    Set<String> before = AcceptanceWorkspaceFiles.snapshot(dir);
    Files.writeString(dir.resolve("extra.md"), "x");
    Files.writeString(dir.resolve("runs.log"), "http://127.0.0.1:9 exit=0\n");

    String problem = AcceptanceWorkspaceFiles.problem(expected, before, dir, "http://127.0.0.1:9");

    assertThat(problem).contains("extra.md");
  }

  @Test
  void namesAWorkspaceWhoseFlowsCommandNeverRanOnTheEnvironment(@TempDir Path dir) {
    AcceptanceCase expected = AcceptanceCases.parse(CASE.formatted("none"));
    AcceptanceWorkspaceFiles.seed(dir, expected);
    Set<String> before = AcceptanceWorkspaceFiles.snapshot(dir);

    String problem = AcceptanceWorkspaceFiles.problem(expected, before, dir, "http://127.0.0.1:9");

    assertThat(problem).contains("never ran BASE_URL=http://127.0.0.1:9 node runflow.mjs");
  }

  @Test
  void ignoresWhatTheAgentCliKeepsInHiddenFoldersAndJagtsOwnReport(@TempDir Path dir)
      throws Exception {
    AcceptanceCase expected = AcceptanceCases.parse(CASE.formatted("none"));
    AcceptanceWorkspaceFiles.seed(dir, expected);
    Set<String> before = AcceptanceWorkspaceFiles.snapshot(dir);
    Files.createDirectories(dir.resolve(".state/sessions"));
    Files.writeString(dir.resolve(".state/sessions/a.json"), "{}");
    Files.writeString(dir.resolve("acceptance-report.md"), "report");
    Files.writeString(dir.resolve("runs.log"), "http://127.0.0.1:9 exit=0\n");

    String problem = AcceptanceWorkspaceFiles.problem(expected, before, dir, "http://127.0.0.1:9");

    assertThat(problem).isEmpty();
  }
}
