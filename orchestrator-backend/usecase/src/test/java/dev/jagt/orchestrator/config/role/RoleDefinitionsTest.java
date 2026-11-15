package dev.jagt.orchestrator.config.role;

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
        .containsExactly("curl -sS*", "git log*", "git show*", "git diff*", "git -C*");
    assertThat(compiled.tools().refuseShell()).contains("git push*");
    assertThat(compiled.tools().refuseRead()).isEmpty();
    assertThat(compiled.tools().git()).isTrue();
    assertThat(compiled.tools().fetch()).isTrue();
  }

  @Test
  void flatRoleDocumentCompilesItsTriggerAndItsRepliesIntoOneValueEach() {
    RoleDefinition compiled =
        RoleDefinitions.compile(
            """
            id: x
            name: X
            model: m
            trigger: [deployed, pipelines-green]
            oncePer: deploy
            outcomes: [round, stop]
            answer:
              - required: verdict
            decline:
              - text: why
            """);

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
  void roleDeclaringNeitherKeyRunsNoJobsAndOpensNoRead() {
    RoleDefinition compiled =
        RoleDefinitions.compile(
            """
            id: x
            name: X
            model: m
            trigger: [deployed]
            oncePer: deploy
            outcomes: [round]
            """);

    assertThat(compiled.checking().jobs()).isFalse();
    assertThat(compiled.checking().readSteps()).isEmpty();
  }

  @Test
  void jobsHoldsOnlyForTheLiteralTrue() {
    RoleDefinition compiled =
        RoleDefinitions.compile(
            """
            id: x
            name: X
            model: m
            trigger: [deployed]
            oncePer: deploy
            outcomes: [round]
            jobs: "true"
            readSteps: [one, two]
            """);

    assertThat(compiled.checking().jobs()).isFalse();
    assertThat(compiled.checking().readSteps()).containsExactly("one", "two");
  }

  @Test
  void copyLackingBothKeysKeepsTheShippedOnes(@TempDir Path folder) throws Exception {
    Path copy = Files.writeString(folder.resolve("role.yml"), "model: opus\n");

    RoleDefinition compiled =
        RoleDefinitions.fromFiles(List.of(Path.of("../../roles/acceptance/role.yml.dist"), copy));

    assertThat(compiled.model()).isEqualTo("opus");
    assertThat(compiled.checking().jobs()).isTrue();
    assertThat(compiled.checking().readSteps()).containsExactly("accept", "again", "decline");
  }

  @Test
  void theShippedAcceptanceRaisesAllThreeRolePointKinds() {
    RoleDefinition compiled =
        RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"));

    assertThat(compiled.points()).containsExactly("passed", "round-sent", "declined");
  }

  @Test
  void refusesPointKindTheVocabularyDoesNotHold() {
    assertThatThrownBy(() ->
            RoleDefinitions.compile(
                """
                id: x
                name: X
                model: m
                trigger: [deployed]
                oncePer: deploy
                outcomes: [round]
                points: [exploded]
                """))
        .isInstanceOf(RoleInvalid.class)
        .hasMessageContaining("exploded");
  }

  @Test
  void refusesTriggerWordTheVocabularyDoesNotHold() {
    assertThatThrownBy(() ->
            RoleDefinitions.compile(
                """
                id: x
                name: X
                model: m
                trigger: [whenever]
                oncePer: deploy
                outcomes: [round]
                """))
        .isInstanceOf(RoleInvalid.class)
        .hasMessageContaining("whenever");
  }

  @Test
  void checkerTriggerWithoutDeployedIsRefused() {
    assertThatThrownBy(() ->
            RoleDefinitions.compile(
                """
                id: x
                name: X
                model: m
                trigger: [pipelines-green]
                oncePer: deploy
                outcomes: [round]
                """))
        .isInstanceOf(RoleInvalid.class)
        .hasMessageContaining("deployed");
  }

  @ParameterizedTest
  @ValueSource(strings = {"cli: [Read]", "allow: [Read]", "refuse: [Write]"})
  void refusesToolsKeyThatMovedNamingTheNewOnes(String oldKey) {
    assertThatThrownBy(() ->
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
  void refusesRoleWhoseReadHasNoOutcome() {
    assertThatThrownBy(() ->
            RoleDefinitions.compile(
                """
                id: x
                name: X
                model: m
                trigger: [deployed]
                oncePer: deploy
                outcomes: []
                """))
        .isInstanceOf(RoleInvalid.class)
        .hasMessageContaining("no exit");
  }

  @Test
  void refusesJobsWithoutReadSteps() {
    assertThatThrownBy(() ->
            RoleDefinitions.compile(
                """
                id: x
                name: X
                model: m
                trigger: [deployed]
                oncePer: deploy
                outcomes: [round]
                jobs: true
                """))
        .isInstanceOf(RoleInvalid.class)
        .hasMessageContaining("x: jobs: true names no readSteps");
  }
}
