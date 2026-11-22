package fitness.gradle.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VulnerabilitiesTest {

  @Test
  void listsOneItemPerPackageAndAdvisory() {
    String json =
        """
        {"results":[{"packages":[
          {"package":{"name":"lib","version":"1.0"},"vulnerabilities":[{"id":"GHSA-1"},{"id":"GHSA-2"}]},
          {"package":{"name":"ok","version":"2.0"}}]}]}
        """;

    assertThat(Vulnerabilities.parse(json)).containsExactly("lib@1.0 GHSA-1", "lib@1.0 GHSA-2");
  }

  @Test
  void findsNoneInAReportWithoutResults() {
    assertThat(Vulnerabilities.parse("{}")).isEmpty();
  }
}
