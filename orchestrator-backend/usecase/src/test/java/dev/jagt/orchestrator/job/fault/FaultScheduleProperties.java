package dev.jagt.orchestrator.job.fault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.jagt.orchestrator.job.fault.FakeFaultWorld.Allowed;
import dev.jagt.orchestrator.job.fault.FakeFaultWorld.Case;
import dev.jagt.orchestrator.job.fault.FakeFaultWorld.Event;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class FaultScheduleProperties {

  private static final int DEPTH = 3;
  private static final List<Allowed> ALLOWED = FakeFaultWorld.allowed();

  @Property(generation = GenerationMode.EXHAUSTIVE)
  void eachNamedScheduleEndsAsItsLinePins(@ForAll("named") Case schedule) {
    try (FakeFaultWorld world = new FakeFaultWorld(schedule.seed())) {
      world.run(schedule.events());

      assertSoftly(soft -> {
        soft.assertThat(world.outcome())
            .as(world.told(schedule.toString()))
            .isEqualTo(schedule.pinned());
        soft.assertThat(world.failures()).as(world.told(schedule.toString())).isEmpty();
      });
    }
  }

  @Property(generation = GenerationMode.EXHAUSTIVE)
  void eachAllowedEndingIsReachedByItsFaultsAlone(@ForAll("allowed") Allowed row) {
    try (FakeFaultWorld world = new FakeFaultWorld(0)) {
      world.runSettling(row.faults());

      assertThat(world.outcome()).as(world.told(row.toString())).isEqualTo(row.ending());
    }
  }

  @Property(generation = GenerationMode.EXHAUSTIVE, tries = 10_000)
  void everyScheduleUpToTheDepthEndsAsAllowedAsksOnlyScriptedPortsAndTellsEachThingOnce(
      @ForAll("schedules") List<Event> faults) {
    try (FakeFaultWorld world = new FakeFaultWorld(0)) {
      world.runSettling(faults);

      assertSoftly(soft -> {
        soft.assertThat(world.outcome())
            .as(world.told(faults.toString()))
            .isIn(Allowed.endings(ALLOWED, faults));
        soft.assertThat(world.failures()).as(world.told(faults.toString())).isEmpty();
        soft.assertThat(world.toldTwice()).as(world.told(faults.toString())).isEmpty();
      });
    }
  }

  @Property(tries = 50)
  void scheduleRunTwiceUnderOneSeedTracesTheSameEvents(
      @ForAll long seed, @ForAll("schedules") List<Event> faults) {
    List<String> first;
    try (FakeFaultWorld world = new FakeFaultWorld(seed)) {
      world.runSettling(faults);
      first = world.trace();
    }
    try (FakeFaultWorld again = new FakeFaultWorld(seed)) {
      again.runSettling(faults);

      assertThat(again.trace()).isEqualTo(first);
    }
  }

  @Provide
  Arbitrary<Case> named() {
    return Arbitraries.of(FakeFaultWorld.cases());
  }

  @Provide
  Arbitrary<Allowed> allowed() {
    return Arbitraries.of(ALLOWED);
  }

  @Provide
  Arbitrary<List<Event>> schedules() {
    return Arbitraries.of(Event.faults()).list().ofMaxSize(DEPTH);
  }
}
