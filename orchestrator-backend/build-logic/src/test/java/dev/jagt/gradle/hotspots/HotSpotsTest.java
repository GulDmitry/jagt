package dev.jagt.gradle.hotspots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.gradle.exec.Git;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HotSpotsTest {

  private static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");
  private static final Instant RECENT = Instant.parse("2026-11-20T00:00:00Z");
  private static final String FIX = "fix\n\nDefect-family: A";
  private static final String HOT = "m/src/main/java/a/Hot.java";

  @Test
  void failsNamingTheClassAndCountWhenOneClassHoldsMoreFixesThanTheCeiling(@TempDir Path root)
      throws Exception {
    TestHistoryBuilder.in(root)
        .commit(HOT, RECENT, FIX)
        .commit(HOT, RECENT, FIX)
        .commit(HOT, RECENT, FIX);

    List<HotSpots.Spot> top =
        new HotSpots(root, new Git(root), Clock.fixed(NOW, ZoneOffset.UTC)).top();

    assertThatThrownBy(() -> HotSpots.enforce(top, 2))
        .isInstanceOf(GradleException.class)
        .hasMessageContaining("Hot has 3 fix commits");
  }

  @Test
  void failsAskingToLowerTheCeilingWhenTheTopCountFellBelowIt(@TempDir Path root) throws Exception {
    TestHistoryBuilder.in(root).commit(HOT, RECENT, FIX);

    List<HotSpots.Spot> top =
        new HotSpots(root, new Git(root), Clock.fixed(NOW, ZoneOffset.UTC)).top();

    assertThatThrownBy(() -> HotSpots.enforce(top, 2))
        .isInstanceOf(GradleException.class)
        .hasMessageContaining("lower hotSpots.max to 1");
  }

  @Test
  void failsNamingTheMissingHistoryWhenTheCheckoutIsShallow(
      @TempDir Path root, @TempDir Path clones) throws Exception {
    TestHistoryBuilder source = TestHistoryBuilder.in(root).commit(HOT, RECENT, FIX);
    Path shallow = clones.resolve("shallow");
    source.shallowCloneTo(shallow);

    HotSpots hotSpots = new HotSpots(shallow, new Git(shallow), Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(hotSpots::top)
        .isInstanceOf(GradleException.class)
        .hasMessageContaining("shallow");
  }

  @Test
  void countsOnlyMainSourceFixesOfTheWindowOtherThanFamilyL(@TempDir Path root) throws Exception {
    TestHistoryBuilder.in(root)
        .commit(HOT, Instant.parse("2026-09-01T00:00:00Z"), FIX)
        .commit(HOT, RECENT, FIX)
        .commit(HOT, RECENT, "test fix\n\nDefect-family: L")
        .commit("m/src/test/java/a/HotTest.java", RECENT, FIX)
        .commit("m/src/main/java/a/Cold.java", RECENT, "feature");

    List<HotSpots.Spot> top =
        new HotSpots(root, new Git(root), Clock.fixed(NOW, ZoneOffset.UTC)).top();

    assertThat(top).extracting(HotSpots.Spot::line).containsExactly("Hot 1 3");
  }
}
