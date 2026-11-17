package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class WorldEventRequestsTest {

  @Test
  void reviewerThreadWaitsWhileNoMergeRequestIsOpen() {
    StandWorld world = mock(StandWorld.class);
    when(world.openRequest()).thenReturn(Optional.empty());

    boolean fired = WorldEvent.reviewerThread("stiff").fire(world);

    assertThat(fired).isFalse();
  }

  @Test
  void reviewerThreadIsAddedToTheOpenMergeRequestAndNoted() {
    StandWorld world = mock(StandWorld.class);
    StandStore store = mock(StandStore.class);
    when(world.store()).thenReturn(store);
    when(world.openRequest())
        .thenReturn(Optional.of(JsonMapper.builder().build().readTree("{\"id\":3}")));

    boolean fired = WorldEvent.reviewerThread("stiff").fire(world);

    assertThat(fired).isTrue();
    verify(store).addThread(3, "greet.sh", 2, "stiff");
    verify(world).note("reviewer thread on merge request 3: stiff");
  }

  @Test
  void anOutsiderCommitWaitsWhileNoMergeRequestIsOpen() {
    StandWorld world = mock(StandWorld.class);
    when(world.openRequest()).thenReturn(Optional.empty());

    boolean fired = WorldEvent.deployBranchCommit("a.txt", "x", "m").fire(world);

    assertThat(fired).isFalse();
  }

  @Test
  void anOutsiderCommitLandsOnTheDeployBranchOfTheOpenMergeRequest() {
    StandWorld world = mock(StandWorld.class);
    StandProject project = mock(StandProject.class);
    Path scratch = Path.of("outsider");
    when(world.openRequest())
        .thenReturn(Optional.of(JsonMapper.builder().build().readTree("{\"id\":3}")));
    when(world.project()).thenReturn(project);
    when(world.scratch("outsider")).thenReturn(scratch);
    when(project.commitOnDeployBranch(scratch, "a.txt", "x", "m")).thenReturn("abc123");

    boolean fired = WorldEvent.deployBranchCommit("a.txt", "x", "m").fire(world);

    assertThat(fired).isTrue();
    verify(world).note(org.mockito.ArgumentMatchers.contains("abc123"));
  }

  @Test
  void theHumanTypesNothingBeforeAnyRelay() {
    StandWorld world = mock(StandWorld.class);
    when(world.relaysSeen()).thenReturn(0);

    boolean fired = WorldEvent.humanLine("wait").fire(world);

    assertThat(fired).isFalse();
    verify(world, never()).say(anyString());
  }

  @Test
  void redThenGreenFailsTheRunningPipelineFirstAndFiresNothing() {
    StandWorld world = mock(StandWorld.class);
    StandStore store = mock(StandStore.class);
    StandProject project = mock(StandProject.class);
    when(world.store()).thenReturn(store);
    when(world.project()).thenReturn(project);
    when(world.openRequest())
        .thenReturn(
            Optional.of(
                JsonMapper.builder()
                    .build()
                    .readTree("{\"id\":4,\"pipeline\":\"running\",\"sourceBranch\":\"f\"}")));
    when(project.head("f")).thenReturn("sha-1");

    boolean fired = WorldEvent.redThenGreen().fire(world);

    assertThat(fired).isFalse();
    verify(store).setPipeline(4, "failed");
  }

  @Test
  void redThenGreenLeavesPipelineThatIsNotRunning() {
    StandWorld world = mock(StandWorld.class);
    StandStore store = mock(StandStore.class);
    when(world.store()).thenReturn(store);
    when(world.openRequest())
        .thenReturn(
            Optional.of(
                JsonMapper.builder().build().readTree("{\"id\":4,\"pipeline\":\"success\"}")));

    WorldEvent.redThenGreen().fire(world);

    verify(store, never()).setPipeline(4, "failed");
  }

  @ParameterizedTest
  @CsvSource({"sha-2,true", "sha-1,false"})
  void redThenGreenRunsThePipelineAgainOnlyOnceTheBranchWasPushed(String head, boolean runsAgain) {
    StandWorld world = mock(StandWorld.class);
    StandStore store = mock(StandStore.class);
    StandProject project = mock(StandProject.class);
    when(world.store()).thenReturn(store);
    when(world.project()).thenReturn(project);
    JsonNode open =
        JsonMapper.builder()
            .build()
            .readTree("{\"id\":4,\"pipeline\":\"running\",\"sourceBranch\":\"f\"}");
    when(world.openRequest()).thenReturn(Optional.of(open));
    when(store.request(4)).thenReturn((ObjectNode) open);
    when(project.head("f")).thenReturn("sha-1", head);
    WorldEvent event = WorldEvent.redThenGreen();
    event.fire(world);

    boolean fired = event.fire(world);

    assertThat(fired).isEqualTo(runsAgain);
  }
}
