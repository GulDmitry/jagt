package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StandReportTest {

    @Test
    void givesAPassRatePerScenarioAndMode() {
        List<String> rates = StandReport.rates(List.of("plan/act passed", "plan/act failed", "plan/act passed",
                "plan/judge passed"));

        assertThat(rates).containsExactly("plan/act: 2 of 3 passed (66%)", "plan/judge: 1 of 1 passed (100%)");
    }

    @Test
    void aSuspendedRunIsNamedAndNeitherPassedNorFailed() {
        List<String> rates = StandReport.rates(List.of("plan/act passed", "plan/act suspended", "plan/judge suspended"));

        assertThat(rates).containsExactly("plan/act: 1 of 1 passed (100%), 1 host suspended",
                "plan/judge: no run counted, 1 host suspended");
    }
}
