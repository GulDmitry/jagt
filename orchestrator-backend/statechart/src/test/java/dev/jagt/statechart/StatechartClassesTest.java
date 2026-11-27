package dev.jagt.statechart;

import static dev.jagt.statechart.TestDoorBuilder.BELL;
import static dev.jagt.statechart.TestDoorBuilder.DOOR;
import static dev.jagt.statechart.TestDoorBuilder.HALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.statechart.TestDoorBuilder.Bell;
import dev.jagt.statechart.TestDoorBuilder.Door;
import dev.jagt.statechart.TestDoorBuilder.Hall;
import dev.jagt.statechart.TestDoorBuilder.Nudge;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatechartClassesTest {

  @Test
  void buildFailsNamingTheStateWhereMoveHasNoClass() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.shovedBothWays();
    builder.onMatching(DOOR, Door.SHUT, Nudge.Tug.class, "tug", nudge -> true).to(Door.OPEN);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("move Tug has no class in state door.OPEN"));
  }

  @Test
  void buildPassesWhereTheMissingStateIsIgnoredWithItsWhy() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.tuggedOnlyWhenShut();

    Statechart<Nudge, String, String> chart = builder.build();

    assertThat(chart.unclassified(chart.configuration(Map.of("door", "OPEN")), new Nudge.Tug()))
        .isEmpty();
  }

  @Test
  void buildPassesWhereTheMissingStateIsRefusedUnderItsName() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.shovedBothWays();
    builder.onMatching(DOOR, Door.SHUT, Nudge.Tug.class, "tug", nudge -> true).to(Door.OPEN);
    builder.refuses(DOOR, Nudge.Tug.class, Set.of(Door.OPEN), "tug on an open door");

    Statechart<Nudge, String, String> chart = builder.build();

    assertThat(chart.unclassified(chart.configuration(Map.of("door", "OPEN")), new Nudge.Tug()))
        .isEmpty();
  }

  @Test
  void buildFailsWhereStateIsDeclaredIgnoredAndTakenToo() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.shovedBothWays();
    builder.onMatching(DOOR, Door.SHUT, Nudge.Tug.class, "tug", nudge -> true).to(Door.OPEN);
    builder.ignores(DOOR, Nudge.Tug.class, Set.of(Door.SHUT, Door.OPEN), "forgotten edge");

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(
                        "move Tug is declared in door.SHUT and also has an edge there"));
  }

  @Test
  void buildPassesWhereTheSameClassIsDeclaredTwiceByTwoRoles() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.tuggedOnlyWhenShut();
    builder.ignores(DOOR, Nudge.Tug.class, Set.of(Door.OPEN), "an open door has nothing to pull");

    assertThat(builder.build()).isNotNull();
  }

  @Test
  void buildFailsWhereStateIsDeclaredTwice() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.tuggedOnlyWhenShut();
    builder.refuses(DOOR, Nudge.Tug.class, Set.of(Door.OPEN), "tug on an open door");

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("move Tug is declared twice in door.OPEN"));
  }

  @Test
  void buildFailsWhereDeclarationHasNoWhy() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.shovedBothWays();
    builder.onMatching(DOOR, Door.SHUT, Nudge.Tug.class, "tug", nudge -> true).to(Door.OPEN);
    builder.ignores(DOOR, Nudge.Tug.class, Set.of(Door.OPEN), " ");

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("move Tug in region door is declared without why"));
  }

  @Test
  void buildFailsWhereMoveHasEdgeInRegionNotAddressedToIt() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.tuggedOnlyWhenShut();
    builder.region(BELL, Bell.QUIET);
    builder.onMatching(BELL, Bell.QUIET, Nudge.Shove.class, "ring", nudge -> true).to(Bell.RINGING);
    builder.onMatching(BELL, Bell.RINGING, Nudge.Shove.class, "hush", nudge -> true).to(Bell.QUIET);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(
                        "move Shove has an edge in region bell, which it is not addressed to"));
  }

  @Test
  void buildFailsNamingTheKindAddressedToNoRegionAndOwnedByNoRole() {
    StatechartBuilder<Nudge, String, String> builder =
        Statechart.<Nudge, String, String>builder()
            .region(DOOR, Door.SHUT)
            .moves(Nudge.class)
            .addressed(Nudge.Shove.class, DOOR);
    builder.onMatching(DOOR, Door.SHUT, Nudge.Shove.class, "shove", nudge -> true).to(Door.OPEN);
    builder.ignores(DOOR, Nudge.Shove.class, Set.of(Door.OPEN), "an open door stays open");

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("move Tug is addressed to no region and owned by no role"));
  }

  @Test
  void buildPassesWhereTheKindAddressedToNoRegionIsOwnedByRole() {
    StatechartBuilder<Nudge, String, String> builder =
        Statechart.<Nudge, String, String>builder()
            .region(DOOR, Door.SHUT)
            .moves(Nudge.class)
            .addressed(Nudge.Shove.class, DOOR)
            .ownedByRole(Nudge.Tug.class, "caretaker");
    builder.onMatching(DOOR, Door.SHUT, Nudge.Shove.class, "shove", nudge -> true).to(Door.OPEN);
    builder.onMatching(DOOR, Door.OPEN, Nudge.Shove.class, "shove", nudge -> true).to(Door.SHUT);

    assertThat(builder.build()).isNotNull();
  }

  @Test
  void buildFailsWhereRoleOwnsWhatIsNoKindOfMove() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.tuggedOnlyWhenShut();
    builder.ownedByRole(Nudge.class, "caretaker");

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems()).containsExactly("move Nudge is not a kind of move"));
  }

  @Test
  void buildFailsWhereTransitionNamesNoKindOfMove() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.shovedBothWays();
    builder.onMatching(DOOR, Door.SHUT, "anything", nudge -> true).to(Door.OPEN);
    builder.ignores(DOOR, Nudge.Tug.class, Set.of(Door.OPEN, Door.SHUT), "no pull is taken");

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("transition anything of door.SHUT names no kind of move"));
  }

  @Test
  void buildFailsWhereMoveIsAddressedThoughNoKindOfMove() {
    StatechartBuilder<Nudge, String, String> builder =
        Statechart.<Nudge, String, String>builder()
            .region(DOOR, Door.SHUT)
            .moves(Nudge.Shove.class)
            .addressed(Nudge.Tug.class, DOOR);
    builder.onMatching(DOOR, Door.SHUT, Nudge.Shove.class, "shove", nudge -> true).to(Door.OPEN);
    builder.onMatching(DOOR, Door.OPEN, Nudge.Shove.class, "shove", nudge -> true).to(Door.SHUT);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid -> assertThat(invalid.problems()).contains("move Tug is not a kind of move"));
  }

  @Test
  void buildFailsWhereMoveIsAddressedToUnknownRegion() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.tuggedOnlyWhenShut();
    builder.addressed(Nudge.Shove.class, BELL);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("move Shove is addressed to unknown region bell"));
  }

  @Test
  void buildFailsWhereClassIsDeclaredInUnknownRegion() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.tuggedOnlyWhenShut();
    builder.ignores(BELL, Nudge.Tug.class, Set.of(Bell.QUIET), "a bell is not pulled");

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("move Tug is declared in unknown region bell"));
  }

  @Test
  void buildFailsWhereClassIsDeclaredInRegionNotAddressedToIt() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.tuggedOnlyWhenShut();
    builder.region(BELL, Bell.QUIET);
    builder.ignores(BELL, Nudge.Tug.class, Set.of(Bell.QUIET), "a bell is not pulled");

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("move Tug is declared in region bell, not addressed to it"));
  }

  @Test
  void buildFailsWhereClassIsDeclaredInStateNoTaskStandsIn() {
    StatechartBuilder<Nudge, String, String> builder =
        Statechart.<Nudge, String, String>builder()
            .region(HALL, Hall.LOBBY)
            .compound(HALL, Hall.STAIRS, Set.of(Hall.FIRST, Hall.SECOND))
            .moves(Nudge.Shove.class)
            .addressed(Nudge.Shove.class, HALL)
            .ignores(HALL, Nudge.Shove.class, Set.of(Hall.STAIRS), "stairs are only passed");

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("move Shove is declared in hall.STAIRS, which no task stands in"));
  }

  @Test
  void edgeOnSupertypeTakesEveryKindUnderIt() {
    StatechartBuilder<Nudge, String, String> builder = TestDoorBuilder.shovedBothWays();
    builder.onMatching(DOOR, Door.SHUT, Nudge.class, "any", nudge -> true).to(Door.OPEN);
    builder.ignores(DOOR, Nudge.Tug.class, Set.of(Door.OPEN), "an open door has nothing to pull");

    Statechart<Nudge, String, String> chart = builder.build();

    assertThat(chart.unclassified(chart.configuration(Map.of("door", "SHUT")), new Nudge.Tug()))
        .isEmpty();
  }

  @Test
  void kindOutsideDeclaredMovesIsUnclassified() {
    StatechartBuilder<Nudge, String, String> builder =
        Statechart.<Nudge, String, String>builder()
            .region(DOOR, Door.SHUT)
            .moves(Nudge.Shove.class)
            .addressed(Nudge.Shove.class, DOOR);
    builder.onMatching(DOOR, Door.SHUT, Nudge.Shove.class, "shove", nudge -> true).to(Door.OPEN);
    builder.onMatching(DOOR, Door.OPEN, Nudge.Shove.class, "shove", nudge -> true).to(Door.SHUT);
    Statechart<Nudge, String, String> chart = builder.build();

    assertThat(chart.unclassified(chart.configuration(Map.of("door", "SHUT")), new Nudge.Tug()))
        .containsExactly("move Tug is not a kind of move");
  }

  @Test
  void chartDeclaringNoMovesClassifiesNothing() {
    StatechartBuilder<Nudge, String, String> builder =
        Statechart.<Nudge, String, String>builder().region(DOOR, Door.SHUT);
    builder.onMatching(DOOR, Door.SHUT, "shove", nudge -> true).to(Door.OPEN);
    builder.onMatching(DOOR, Door.OPEN, "shove", nudge -> true).to(Door.SHUT);
    Statechart<Nudge, String, String> chart = builder.build();

    assertThat(chart.unclassified(chart.configuration(Map.of("door", "SHUT")), new Nudge.Shove()))
        .isEmpty();
  }
}
