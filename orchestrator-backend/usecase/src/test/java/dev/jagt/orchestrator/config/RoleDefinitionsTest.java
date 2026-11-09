package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoleDefinitionsTest {

  @Test
  void theAcceptanceCompiledFromItsRoleYmlIsTheAcceptanceDeclaredInCode() {
    RoleDefinition compiled =
        RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"));

    assertThat(compiled.id()).isEqualTo("acceptance");
    assertThat(compiled.trigger().trigger())
        .containsExactly("deployed", "pipelines-green", "environment-named");
    assertThat(compiled.trigger().oncePer()).isEqualTo("deploy");
    assertThat(compiled.replies().outcomes())
        .containsExactly("round", "question", "wait", "dispute", "stop");
    assertThat(compiled.tools().shell())
        .containsExactly("curl -sS*", "git log*", "git show*", "git diff*");
    assertThat(compiled.tools().refuseShell()).contains("git push*");
    assertThat(compiled.tools().refuseRead()).isEmpty();
    assertThat(compiled.tools().git()).isTrue();
    assertThat(compiled.tools().fetch()).isTrue();
  }

  @Test
  void aFlatRoleDocumentCompilesItsTriggerAndItsRepliesIntoOneValueEach() {
    RoleDefinition compiled =
        RoleDefinitions.compile(
            "id: x\n"
                + "name: X\n"
                + "model: m\n"
                + "trigger: [deployed, pipelines-green]\n"
                + "oncePer: deploy\n"
                + "outcomes: [round, stop]\n"
                + "answer:\n"
                + "  - required: verdict\n"
                + "decline:\n"
                + "  - text: why\n");

    assertThat(compiled.trigger().trigger()).containsExactly("deployed", "pipelines-green");
    assertThat(compiled.trigger().oncePer()).isEqualTo("deploy");
    assertThat(compiled.replies().outcomes()).containsExactly("round", "stop");
    assertThat(compiled.replies().answer().json()).contains("verdict");
    assertThat(compiled.replies().decline().json()).contains("why");
  }

  @Test
  void theShippedAcceptanceRunsJobsAndItsReadOpensOnAcceptAgainAndDecline() {
    RoleDefinition compiled =
        RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"));

    assertThat(compiled.checking().jobs()).isTrue();
    assertThat(compiled.checking().readSteps()).containsExactly("accept", "again", "decline");
  }

  @Test
  void aRoleDeclaringNeitherKeyRunsNoJobsAndOpensNoRead() {
    RoleDefinition compiled =
        RoleDefinitions.compile(
            "id: x\nname: X\nmodel: m\ntrigger: [deployed]\n"
                + "oncePer: deploy\noutcomes: [round]\n");

    assertThat(compiled.checking().jobs()).isFalse();
    assertThat(compiled.checking().readSteps()).isEmpty();
  }

  @Test
  void jobsHoldsOnlyForTheLiteralTrue() {
    RoleDefinition compiled =
        RoleDefinitions.compile(
            "id: x\nname: X\nmodel: m\ntrigger: [deployed]\n"
                + "oncePer: deploy\noutcomes: [round]\njobs: \"true\"\nreadSteps: [one, two]\n");

    assertThat(compiled.checking().jobs()).isFalse();
    assertThat(compiled.checking().readSteps()).containsExactly("one", "two");
  }

  @Test
  void aCopyLackingBothKeysKeepsTheShippedOnes(@TempDir Path folder) throws Exception {
    Path copy = Files.writeString(folder.resolve("role.yml"), "model: opus\n");

    RoleDefinition compiled =
        RoleDefinitions.fromFiles(List.of(Path.of("../../roles/acceptance/role.yml.dist"), copy));

    assertThat(compiled.model()).isEqualTo("opus");
    assertThat(compiled.checking().jobs()).isTrue();
    assertThat(compiled.checking().readSteps()).containsExactly("accept", "again", "decline");
  }

  @Test
  void refusesATriggerWordTheVocabularyDoesNotHold() {
    assertThatThrownBy(
            () ->
                RoleDefinitions.compile(
                    "id: x\nname: X\nmodel: m\ntrigger: [whenever]\n"
                        + "oncePer: deploy\noutcomes: [round]\n"))
        .isInstanceOf(RoleInvalid.class)
        .hasMessageContaining("whenever");
  }

  @Test
  void aCheckerTriggerWithoutDeployedIsRefused() {
    assertThatThrownBy(
            () ->
                RoleDefinitions.compile(
                    "id: x\nname: X\nmodel: m\ntrigger: [pipelines-green]\n"
                        + "oncePer: deploy\noutcomes: [round]\n"))
        .isInstanceOf(RoleInvalid.class)
        .hasMessageContaining("deployed");
  }

  @ParameterizedTest
  @ValueSource(strings = {"cli: [Read]", "allow: [Read]", "refuse: [Write]"})
  void refusesAToolsKeyThatMovedNamingTheNewOnes(String oldKey) {
    assertThatThrownBy(
            () ->
                RoleDefinitions.compile(
                    "id: x\nname: X\nmodel: m\ntrigger: [deployed]\n"
                        + "oncePer: deploy\noutcomes: [round]\ntools:\n  "
                        + oldKey
                        + "\n"))
        .isInstanceOf(RoleInvalid.class)
        .hasMessageContaining("tools.shell")
        .hasMessageContaining("tools.refuseShell")
        .hasMessageContaining("tools.refuseRead")
        .hasMessageContaining(oldKey.substring(0, oldKey.indexOf(':')));
  }

  @Test
  void refusesARoleWhoseReadHasNoOutcome() {
    assertThatThrownBy(
            () ->
                RoleDefinitions.compile(
                    "id: x\nname: X\nmodel: m\ntrigger: [deployed]\n"
                        + "oncePer: deploy\noutcomes: []\n"))
        .isInstanceOf(RoleInvalid.class)
        .hasMessageContaining("no exit");
  }
}
