package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.VendorNamings.Naming;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VendorNamesTest {

  private static final String JOB = "usecase/src/main/java/dev/jagt/orchestrator/job/";
  private static final String SERVICE = "usecase/src/main/java/dev/jagt/orchestrator/service/";

  private static final Map<String, String> ALLOWED =
      Map.ofEntries(
          Map.entry(
              JOB + "RunningJarWatch.java gradlew", "names jagt's own build tool; iteration 2"),
          Map.entry(
              SERVICE + "WorktreeSandbox.java gradle",
              "build tools' cache directories; iteration 2"),
          Map.entry(
              SERVICE + "WorktreeSandbox.java npm", "build tools' cache directories; iteration 2"),
          Map.entry(
              SERVICE + "WorktreeNoise.java gradle", "a build tool's directory; iteration 2"));

  @Test
  void namesNoVendorOfAnAgentTerminalEditorCodeHostTrackerBuildToolOrModelOutsideTheAdapters() {
    List<String> named =
        VendorNamings.outsideTheAdapters()
            .filter(naming -> !ALLOWED.containsKey(naming.key()))
            .map(Naming::where)
            .toList();

    assertThat(named).isEmpty();
  }

  @Test
  void keepsNoAllowedNamingThatTheFileNoLongerHolds() {
    List<String> held = VendorNamings.outsideTheAdapters().map(Naming::key).distinct().toList();

    assertThat(ALLOWED.keySet()).isSubsetOf(held);
  }
}
