package dev.jagt.statechart;

import static dev.jagt.statechart.TestHistoryBuilder.PLACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.statechart.TestHistoryBuilder.Place;
import dev.jagt.statechart.TestIrpBuilder.Ev;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatechartHistoryTest {

  private final Statechart<Ev, String, String> chart = TestHistoryBuilder.deepThenShallow();

  @Test
  void compoundExitRecordsTheLeafUnderEachOfItsHistories() {
    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("first"));

    assertThat(step.configuration().history())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("place.S0.SHALLOW", "S011", "place.S0.DEEP", "S011"));
  }

  @Test
  void historyRoundTripsThroughThePlainForm() {
    Configuration moved = chart.step(chart.initial(), "", new Ev("first")).configuration();

    Configuration restored = chart.configuration(moved.toPlain(), moved.history());

    assertThat(restored).isEqualTo(moved).hasSameHashCodeAs(moved);
  }

  @Test
  void flatPlainMapReadsAsEmptyHistory() {
    Configuration read = chart.configuration(Map.of("place", "S012"));

    assertThat(read.history()).isEmpty();
    assertThat(read).isEqualTo(chart.configuration(Map.of("place", "S012"), Map.of()));
  }

  @Test
  void historyNamingAnUnknownLeafIsRejected() {
    Map<String, String> plain = Map.of("place", "S012");

    assertThatThrownBy(() -> chart.configuration(plain, Map.of("place.S0.DEEP", "S3")))
        .isInstanceOfSatisfying(
            InvalidConfiguration.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("history place.S0.DEEP has no known leaf, got S3"));
  }

  @Test
  void historyDefaultOutsideItsCompoundIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestHistoryBuilder.skeleton()
            .region(PLACE, Place.S3, Set.of(Place.PASS))
            .history(PLACE, Place.S0, History.DEEP, Place.S3)
            .always(PLACE, Place.S3)
            .to(Place.PASS);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("history place.S0 has default S3 outside its compound"));
  }

  @Test
  void historyOnNonCompoundStateIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestHistoryBuilder.skeleton()
            .region(PLACE, Place.S3, Set.of(Place.PASS))
            .history(PLACE, Place.S011, History.SHALLOW, Place.S012)
            .always(PLACE, Place.S3)
            .to(Place.PASS);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("history place.S011 is not on a compound state"));
  }

  @Test
  void historyDeclaredTwiceWithOneKindIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestHistoryBuilder.skeleton()
            .region(PLACE, Place.S3, Set.of(Place.PASS))
            .history(PLACE, Place.S0, History.DEEP, Place.S022)
            .history(PLACE, Place.S0, History.DEEP, Place.S021)
            .always(PLACE, Place.S3)
            .to(Place.PASS);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("history place.S0 is declared more than once"));
  }

  @Test
  void transitionIntoAnUndeclaredHistoryIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestHistoryBuilder.skeleton()
            .region(PLACE, Place.S3, Set.of(Place.PASS))
            .always(PLACE, Place.S3)
            .toHistory(Place.S0, History.SHALLOW);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("transition place S3->S0 enters an undeclared history"));
  }

  @Test
  void restoreEntersFromTheCompoundDownAtTheDepthTheHistoryRemembers() {
    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("first"));

    assertThat(step.effects()).containsExactly("S0", "S01", "S012", "S0", "S01", "S011");
  }

  @Test
  void defaultOfTheHistoryEntersTheEnclosingStatesBeforeIt() {
    Statechart<Ev, String, String> nested =
        TestHistoryBuilder.skeleton()
            .region(PLACE, Place.S3, Set.of(Place.PASS))
            .history(PLACE, Place.S01, History.DEEP, Place.S012)
            .onEntry(PLACE, Place.S0, "S0")
            .onEntry(PLACE, Place.S01, "S01")
            .onEntry(PLACE, Place.S012, "S012")
            .onMatching(PLACE, Place.S0, "x", e -> e.name().equals("x"))
            .to(Place.S022)
            .onMatching(PLACE, Place.S0, "y", e -> e.name().equals("y"))
            .to(Place.S021)
            .always(PLACE, Place.S3)
            .toHistory(Place.S01, History.DEEP)
            .onMatching(PLACE, Place.S3, "z", e -> e.name().equals("z"))
            .to(Place.S4)
            .always(PLACE, Place.S4)
            .to(Place.PASS)
            .build();

    Macrostep<String> step = nested.step(nested.initial(), "", new Ev("start"));

    assertThat(step.effects()).containsExactly("S0", "S01", "S012");
  }

  @Test
  void statesOutsideTheHistoryCompoundStayUnreachable() {
    StatechartBuilder<Ev, String, String> builder =
        TestHistoryBuilder.skeleton()
            .region(PLACE, Place.S3, Set.of(Place.PASS))
            .history(PLACE, Place.S01, History.DEEP, Place.S012)
            .always(PLACE, Place.S3)
            .toHistory(Place.S01, History.DEEP);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("state place.S02 is unreachable")
                    .doesNotContain("state place.S012 is unreachable"));
  }

  @Test
  void historyOfCompoundTheChartNeverDeclaredIsRejected() {
    Map<String, String> plain = Map.of("place", "S012");

    assertThatThrownBy(() -> chart.configuration(plain, Map.of("place.S3.DEEP", "S012")))
        .isInstanceOfSatisfying(
            InvalidConfiguration.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("history place.S3.DEEP has no known leaf, got S012"));
  }

  @Test
  void renderedChartMarksTransitionThatEntersHistory() {
    assertThat(chart.render()).contains("always [DEEP history]");
  }
}
