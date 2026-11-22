package dev.jagt.statechart;

import static dev.jagt.statechart.TestIrpBuilder.NODE;
import static dev.jagt.statechart.TestIrpBuilder.PEER;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.statechart.TestHistoryBuilder.Twin;
import dev.jagt.statechart.TestIrpBuilder.Ev;
import dev.jagt.statechart.TestIrpBuilder.Node;
import dev.jagt.statechart.TestIrpBuilder.Peer;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatechartScxmlConformanceTest {

  @Test
  void irp355EntersTheFirstStateInDocumentOrderWithoutAnInitial() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton().region(NODE, TestIrpBuilder.finals()).build();

    assertThat(chart.initial().toPlain()).containsExactly(Map.entry("node", "S01"));
  }

  @Test
  void irp364EntersTheDefaultInitialChildOfTheCompoundTarget() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onMatching(NODE, Node.S0, "go", e -> e.name().equals("go"))
            .to(Node.S1)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "S11"));
  }

  @Test
  void irp372RaisesDoneForTheParentAfterTheFinalChildsOnEntry() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, Set.of(Node.PASS, Node.FAIL, Node.S12))
            .completion(
                done -> new Ev(done instanceof Completed.OfState s ? "done." + s.state() : "chart"))
            .onEntry(NODE, Node.S12, "enter S12")
            .onMatching(NODE, Node.S1, "done.S1", e -> e.name().equals("done.S1"))
            .emit("done")
            .to(Node.PASS)
            .build();

    Macrostep<String> step =
        chart.step(chart.configuration(Map.of("node", "S11")), "", new Ev("next"));

    assertThat(step.effects()).containsExactly("enter S12", "done");
    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "PASS"));
  }

  @Test
  void irp375RunsOnEntryHandlersInDocumentOrder() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onEntry(NODE, Node.S01, "first")
            .onEntry(NODE, Node.S01, "second")
            .onMatching(NODE, Node.S02, "back", e -> e.name().equals("back"))
            .to(Node.S01)
            .build();

    Macrostep<String> step =
        chart.step(chart.configuration(Map.of("node", "S02")), "", new Ev("back"));

    assertThat(step.effects()).containsExactly("first", "second");
  }

  @Test
  void irp376TreatsEachOnEntryHandlerAsItsOwnBlock() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onEntry(NODE, Node.S1, "same")
            .onEntry(NODE, Node.S1, "same")
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("enter1"));

    assertThat(step.effects()).containsExactly("same", "same");
  }

  @Test
  void irp377RunsOnExitHandlersInDocumentOrder() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onExit(NODE, Node.S01, "first")
            .onExit(NODE, Node.S01, "second")
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("next"));

    assertThat(step.effects()).containsExactly("first", "second");
  }

  @Test
  void irp378TreatsEachOnExitHandlerAsItsOwnBlock() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onExit(NODE, Node.S01, "same")
            .onExit(NODE, Node.S01, "same")
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("next"));

    assertThat(step.effects()).containsExactly("same", "same");
  }

  @Test
  void irp404ExitsTheExitSetChildrenFirst() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onExit(NODE, Node.S0, "exit S0")
            .onExit(NODE, Node.S01, "exit S01")
            .onMatching(NODE, Node.S0, "go", e -> e.name().equals("go"))
            .to(Node.S1)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("exit S01", "exit S0");
  }

  @Test
  void irp405RunsTransitionContentAfterTheExits() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onExit(NODE, Node.S01, "exit")
            .onMatching(NODE, Node.S0, "go", e -> e.name().equals("go"))
            .emit("act")
            .to(Node.S1)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("exit", "act");
  }

  @Test
  void irp406EntersTheEntrySetAfterTransitionContent() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .onMatching(NODE, Node.S0, "go", e -> e.name().equals("go"))
            .emit("act")
            .to(Node.S1)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onEntry(NODE, Node.S1, "enter S1")
            .onEntry(NODE, Node.S11, "enter S11")
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("act", "enter S1", "enter S11");
  }

  @Test
  void irp407RunsTheOnExitContentOfTheStateLeft() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onExit(NODE, Node.S11, "exit S11")
            .build();

    Macrostep<String> step =
        chart.step(chart.configuration(Map.of("node", "S11")), "", new Ev("next"));

    assertThat(step.effects()).containsExactly("exit S11");
  }

  @Test
  void irp411MakesTheStateActiveAndRunsItsOnEntry() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onEntry(NODE, Node.S12, "enter S12")
            .build();

    Macrostep<String> step =
        chart.step(chart.configuration(Map.of("node", "S11")), "", new Ev("next"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "S12"));
    assertThat(step.effects()).containsExactly("enter S12");
  }

  @Test
  void irp412RunsTheInitialTransitionContentAfterTheCompoundsOnEntry() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .onEntry(NODE, Node.S1, "enter S1")
            .onInitial(NODE, Node.S1, "initial")
            .onEntry(NODE, Node.S11, "enter S11")
            .onMatching(NODE, Node.S0, "go", e -> e.name().equals("go"))
            .to(Node.S1)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("enter S1", "initial", "enter S11");
  }

  @Test
  void irp413StartsInTheStateTheInitialAttributeNames() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S1, TestIrpBuilder.finals())
            .onMatching(NODE, Node.S1, "back", e -> e.name().equals("back"))
            .to(Node.S0)
            .build();

    assertThat(chart.initial().toPlain()).containsExactly(Map.entry("node", "S11"));
  }

  @Test
  void irp403aPrefersTheChildsFirstMatchAndFallsBackToTheParent() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .onMatching(NODE, Node.S0, "event1", e -> e.name().equals("event1"))
            .to(Node.FAIL)
            .onMatching(NODE, Node.S0, "event2", e -> e.name().equals("event2"))
            .to(Node.PASS)
            .onMatching(NODE, Node.S01, "event1", e -> e.name().equals("event1"))
            .to(Node.S02)
            .onMatching(NODE, Node.S01, "any", e -> true)
            .to(Node.FAIL)
            .onMatching(NODE, Node.S02, "event1", e -> e.name().equals("event1"))
            .to(Node.FAIL)
            .onMatching(NODE, Node.S02, "event2", e -> e.name().equals("event2"))
            .when((active, e) -> false)
            .to(Node.FAIL)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .build();

    Macrostep<String> first = chart.step(chart.initial(), "", new Ev("event1"));
    Macrostep<String> second = chart.step(first.configuration(), "", new Ev("event2"));

    assertThat(first.configuration().toPlain()).containsExactly(Map.entry("node", "S02"));
    assertThat(second.configuration().toPlain()).containsExactly(Map.entry("node", "PASS"));
  }

  @Test
  void irp436ReadsInAcrossParallelRegions() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .onMatching(NODE, Node.S0, "any", e -> true)
            .when((active, e) -> active.in(PEER, Peer.S1))
            .to(Node.FAIL)
            .onMatching(NODE, Node.S0, "any", e -> true)
            .when((active, e) -> active.in(PEER, Peer.PS1))
            .to(Node.PASS)
            .onMatching(PEER, Peer.PS1, "swap", e -> e.name().equals("swap"))
            .to(Peer.S1)
            .onMatching(PEER, Peer.S1, "swap", e -> e.name().equals("swap"))
            .to(Peer.PS1)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .region(PEER, Peer.PS1)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.configuration().toPlain()).containsEntry("node", "PASS");
  }

  @Test
  void inHoldsForAnAncestorOfTheActiveLeafEvenInTheTransitionsOwnRegion() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .onMatching(NODE, Node.S11, "go", e -> e.name().equals("go"))
            .when((active, e) -> active.in(NODE, Node.S1))
            .to(Node.S12)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .build();

    Macrostep<String> step =
        chart.step(chart.configuration(Map.of("node", "S11")), "", new Ev("go"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "S12"));
  }

  @Test
  void irp503TargetlessTransitionDoesNotExitItsSource() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .onExit(NODE, Node.S02, "exit")
            .raiseOnEntry(NODE, Node.S02, new Ev("foo"))
            .onMatching(NODE, Node.S02, "foo", e -> e.name().equals("foo"))
            .emit("act")
            .internal()
            .region(NODE, Node.S01, TestIrpBuilder.finals())
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("next"));

    assertThat(step.effects()).containsExactly("act");
  }

  @Test
  void irp505InternalTransitionIntoDescendantKeepsItsSourceEntered() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .onExit(NODE, Node.S1, "exit S1")
            .onExit(NODE, Node.S11, "exit S11")
            .raiseOnEntry(NODE, Node.S1, new Ev("foo"))
            .onMatching(NODE, Node.S1, "foo", e -> e.name().equals("foo"))
            .emit("act")
            .within(Node.S11)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("enter1"));

    assertThat(step.effects()).containsExactly("exit S11", "act");
  }

  @Test
  void irp506AnInternalTransitionToItsOwnSourceBehavesAsExternal() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .onExit(NODE, Node.S0, "exit S0")
            .onExit(NODE, Node.S01, "exit S01")
            .raiseOnEntry(NODE, Node.S1, new Ev("foo"))
            .always(NODE, Node.S1)
            .to(Node.S0)
            .onMatching(NODE, Node.S0, "foo", e -> e.name().equals("foo"))
            .emit("act")
            .within(Node.S0)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("enter1"));

    assertThat(step.effects()).containsExactly("exit S01", "exit S0", "exit S01", "exit S0", "act");
  }

  @Test
  void irp144RunsRaisedEventsInTheOrderTheyWereRaised() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .raiseOnEntry(NODE, Node.S02, new Ev("foo"))
            .raiseOnEntry(NODE, Node.S02, new Ev("bar"))
            .onMatching(NODE, Node.S02, "foo", e -> e.name().equals("foo"))
            .to(Node.S11)
            .onMatching(NODE, Node.S02, "any", e -> true)
            .to(Node.FAIL)
            .onMatching(NODE, Node.S11, "bar", e -> e.name().equals("bar"))
            .to(Node.PASS)
            .onMatching(NODE, Node.S11, "any", e -> true)
            .to(Node.FAIL)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("next"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "PASS"));
  }

  @Test
  void irp396TakesTheFirstTransitionInDocumentOrderForRaisedEvent() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .raiseOnEntry(NODE, Node.S1, new Ev("foo"))
            .onMatching(NODE, Node.S1, "foo", e -> e.name().equals("foo"))
            .to(Node.PASS)
            .onMatching(NODE, Node.S1, "foo", e -> e.name().equals("foo"))
            .to(Node.FAIL)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("enter1"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "PASS"));
  }

  @Test
  void irp415HaltsOnEnteringFinalStateOfTheChart() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton().region(NODE, Node.S0, TestIrpBuilder.finals()).build();

    Macrostep<String> step =
        chart.step(chart.configuration(Map.of("node", "S11")), "", new Ev("end"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "PASS"));
    assertThat(step.halted()).isTrue();
  }

  @Test
  void irp416RaisesDoneForCompoundWhoseEventlessTransitionEntersItsFinalChild() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, Set.of(Node.PASS, Node.FAIL, Node.S02))
            .completion(
                done -> new Ev(done instanceof Completed.OfState s ? "done." + s.state() : "chart"))
            .onMatching(NODE, Node.S1, "go", e -> e.name().equals("go"))
            .to(Node.S0)
            .always(NODE, Node.S01)
            .to(Node.S02)
            .onMatching(NODE, Node.S0, "done.S0", e -> e.name().equals("done.S0"))
            .to(Node.PASS)
            .build();

    Macrostep<String> step =
        chart.step(chart.configuration(Map.of("node", "S11")), "", new Ev("go"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "PASS"));
  }

  @Test
  void irp419TakesEventlessTransitionBeforeRaisedEvent() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .raiseOnEntry(NODE, Node.S02, new Ev("internalEvent"))
            .onMatching(NODE, Node.S02, "any", e -> true)
            .to(Node.FAIL)
            .always(NODE, Node.S02)
            .to(Node.PASS)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("next"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "PASS"));
  }

  @Test
  void irp421DropsRaisedEventsNoTransitionTakesUntilOneIs() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .raiseOnEntry(NODE, Node.S1, new Ev("internalEvent1"))
            .raiseOnEntry(NODE, Node.S1, new Ev("internalEvent2"))
            .raiseOnEntry(NODE, Node.S1, new Ev("internalEvent3"))
            .raiseOnEntry(NODE, Node.S1, new Ev("internalEvent4"))
            .onMatching(NODE, Node.S11, "internalEvent3", e -> e.name().equals("internalEvent3"))
            .to(Node.S12)
            .onMatching(NODE, Node.S12, "internalEvent4", e -> e.name().equals("internalEvent4"))
            .to(Node.PASS)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("enter1"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("node", "PASS"));
  }

  @Test
  void irp423IgnoresExternalEventsNoTransitionTakesUntilOneIs() {
    Statechart<Ev, String, String> chart =
        TestIrpBuilder.skeleton()
            .raiseOnEntry(NODE, Node.S02, new Ev("internalEvent"))
            .onMatching(NODE, Node.S02, "internalEvent", e -> e.name().equals("internalEvent"))
            .to(Node.S12)
            .onMatching(NODE, Node.S02, "any", e -> true)
            .to(Node.FAIL)
            .onMatching(NODE, Node.S12, "externalEvent2", e -> e.name().equals("externalEvent2"))
            .to(Node.PASS)
            .onMatching(NODE, Node.S12, "internalEvent", e -> e.name().equals("internalEvent"))
            .to(Node.FAIL)
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .build();

    Macrostep<String> entered = chart.step(chart.initial(), "", new Ev("next"));
    Macrostep<String> ignored = chart.step(entered.configuration(), "", new Ev("externalEvent1"));
    Macrostep<String> taken = chart.step(ignored.configuration(), "", new Ev("externalEvent2"));

    assertThat(ignored.configuration().toPlain()).containsExactly(Map.entry("node", "S12"));
    assertThat(taken.configuration().toPlain()).containsExactly(Map.entry("node", "PASS"));
  }

  @Test
  void irp387EntersTheDefaultOfTheHistoryWhoseParentWasNeverVisited() {
    RegionId<Twin> twin = new RegionId<>("twin", Twin.class);
    Statechart<Ev, String, String> chart =
        Statechart.<Ev, String, String>builder()
            .region(twin, Twin.S3, Set.of(Twin.PASS))
            .compound(twin, Twin.S0, Twin.S01, Twin.S01, Twin.S02)
            .compound(twin, Twin.S1, Twin.S11, Twin.S11, Twin.S12)
            .compound(twin, Twin.S01, Twin.S011, Twin.S011)
            .compound(twin, Twin.S02, Twin.S021, Twin.S021, Twin.S022)
            .compound(twin, Twin.S11, Twin.S111, Twin.S111)
            .compound(twin, Twin.S12, Twin.S121, Twin.S121, Twin.S122)
            .history(twin, Twin.S0, History.SHALLOW, Twin.S01)
            .history(twin, Twin.S0, History.DEEP, Twin.S022)
            .history(twin, Twin.S1, History.SHALLOW, Twin.S11)
            .history(twin, Twin.S1, History.DEEP, Twin.S122)
            .raiseOnEntry(twin, Twin.S011, new Ev("enteringS011"))
            .raiseOnEntry(twin, Twin.S122, new Ev("enteringS122"))
            .onMatching(twin, Twin.S0, "enteringS011", e -> e.name().equals("enteringS011"))
            .to(Twin.S4)
            .onMatching(twin, Twin.S1, "enteringS122", e -> e.name().equals("enteringS122"))
            .to(Twin.PASS)
            .always(twin, Twin.S3)
            .toHistory(Twin.S0, History.SHALLOW)
            .always(twin, Twin.S4)
            .toHistory(Twin.S1, History.DEEP)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("start"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("twin", "PASS"));
  }

  @Test
  void irp388ResumesTheStoredConfigurationOfTheVisitedHistoryParent() {
    Statechart<Ev, String, String> chart = TestHistoryBuilder.deepThenShallow();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("first"));

    assertThat(step.configuration().toPlain()).containsExactly(Map.entry("place", "PASS"));
  }
}
