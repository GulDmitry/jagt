package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldEventTest {

  @Test
  void jagtIsRestartedOnTheFirstRecognisedRelayOfPendingStep() {
    StandWorld world = mock(StandWorld.class);
    when(world.relaysSeen()).thenReturn(1);

    boolean fired = WorldEvent.restart().fire(world);

    assertThat(fired).isTrue();
    verify(world).restart(Restart.JVM);
  }

  @Test
  void jagtIsNotRestartedBeforeAnyRelay() {
    StandWorld world = mock(StandWorld.class);
    when(world.relaysSeen()).thenReturn(0);

    boolean fired = WorldEvent.restart().fire(world);

    assertThat(fired).isFalse();
  }

  @Test
  void theHumanTypesOnTheFirstRecognisedRelay() {
    StandWorld world = mock(StandWorld.class);
    when(world.relaysSeen()).thenReturn(1);

    boolean fired = WorldEvent.humanLine("wait").fire(world);

    assertThat(fired).isTrue();
    verify(world).say("wait");
  }

  @Test
  void theUnitIsReplacedOnTheFirstRecognisedRelayWhenTheEventAsksForIt() {
    StandWorld world = mock(StandWorld.class);
    when(world.relaysSeen()).thenReturn(1);

    boolean fired = WorldEvent.restart(Restart.UNIT).fire(world);

    assertThat(fired).isTrue();
    verify(world).restart(Restart.UNIT);
  }

  @Test
  void ownRequestIsListedOnTheHostOnceTheTaskBranchIsPushed(@TempDir Path directory) {
    StandWorld world = mock(StandWorld.class);
    StandProject project = mock(StandProject.class);
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    when(world.store()).thenReturn(store);
    when(world.project()).thenReturn(project);
    when(project.pushedBranch()).thenReturn("abc-1");
    when(project.head("abc-1")).thenReturn("sha1");
    when(project.diff("sha1")).thenReturn("a diff");

    boolean fired = WorldEvent.ownRequestListed(1, "ABC-1 Title").fire(world);

    assertThat(fired).isTrue();
    assertThat(store.pull(1).path("head").asString()).isEqualTo("sha1");
    assertThat(store.pull(1).path("awaiting").asBoolean()).isFalse();
    verify(world).note("request 1 of abc-1 listed on the host");
  }

  @Test
  void ownRequestIsNotListedBeforeAnyBranchIsPushed() {
    StandWorld world = mock(StandWorld.class);
    StandProject project = mock(StandProject.class);
    when(world.project()).thenReturn(project);
    when(project.pushedBranch()).thenReturn("");

    boolean fired = WorldEvent.ownRequestListed(1, "ABC-1 Title").fire(world);

    assertThat(fired).isFalse();
  }
}
