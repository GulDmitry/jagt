package dev.jagt.orchestrator.flow.role;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PointsTest {

  @Test
  void roleRaisesOnlyTheKindsItDeclaresEachUnderItsOwnId() {
    List<CheckerRole> roles =
        List.of(
            new CheckerRole("acceptance", true, List.of(), List.of(RolePointKind.PASSED)),
            new CheckerRole("lint", false, List.of(), List.of()));

    assertThat(Points.all(roles))
        .extracting(Point::id)
        .contains("acceptance-passed")
        .doesNotContain("acceptance-declined")
        .noneMatch(id -> id.startsWith("lint-"));
  }

  @Test
  void rolePointSaysWhatMakesItStandAndIsNeverRaised() {
    List<CheckerRole> roles =
        List.of(new CheckerRole("acceptance", true, List.of(), List.of(RolePointKind.DECLINED)));

    assertThat(Points.all(roles))
        .filteredOn(point -> point.id().equals("acceptance-declined"))
        .singleElement()
        .isEqualTo(new Point("acceptance-declined", "the role declined the check", false));
  }

  @Test
  void rolePointKindIsFoundByItsIdAndNoOtherWord() {
    assertThat(RolePointKind.ofId("round-sent")).contains(RolePointKind.ROUND_SENT);
    assertThat(RolePointKind.ofId("exploded")).isEmpty();
  }

  @Test
  void rolePointKindNamesItselfByItsId() {
    assertThat(RolePointKind.ROUND_SENT.id()).isEqualTo("round-sent");
  }
}
