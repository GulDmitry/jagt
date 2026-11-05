package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.service.InstalledRoles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeCheckTest {

    private final InstalledRoles roles = new InstalledRoles(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("../..")));

    @ParameterizedTest
    @CsvSource({
            "ftp://127.0.0.1:8080,qwen-4b,orchestrator.opencode.endpoint",
            "not a url,qwen-4b,orchestrator.opencode.endpoint",
            "http://127.0.0.1:8080/v1,'',orchestrator.opencode.model"})
    void refusesToStartWhereTheSessionsEndpointCannotBeUsed(String endpoint, String model, String key) {
        var check = new OpenCodeCheck(new OpenCodeProperties("sh", endpoint, model, null), roles, "opencode");

        assertThat(check.problems()).extracting(Problem::key).containsExactly(key);
    }

    @Test
    void refusesToStartWithoutTheCliOnPathWhereSessionsRunOnIt() {
        var check = new OpenCodeCheck(new OpenCodeProperties("no-such-agent-cli", "", "", null), roles, "opencode");

        assertThat(check.problems()).extracting(Problem::key).containsExactly("orchestrator.opencode.command");
    }

    @Test
    void refusesToStartWithoutTheCliOnPathWhereARoleReadsOnIt() {
        var check = new OpenCodeCheck(new OpenCodeProperties("no-such-agent-cli", "http://127.0.0.1:8080/v1", "qwen-4b",
                Map.of("qa", "")), roles, "claude");

        assertThat(check.problems()).extracting(Problem::key).containsExactly("orchestrator.opencode.command");
    }

    @Test
    void asksNothingWhereNoSessionAndNoReadRunsOnOpenCode() {
        var check = new OpenCodeCheck(new OpenCodeProperties("no-such-agent-cli", "ftp://x", "", null), roles, "claude");

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void acceptsALocalEndpointWithItsModelForTheSessions() {
        var check = new OpenCodeCheck(new OpenCodeProperties("sh", "http://127.0.0.1:8080/v1", "qwen-4b", null), roles,
                "opencode");

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void refusesAReadKeyThatIsNeitherMasterNorAnInstalledRole() {
        var check = new OpenCodeCheck(new OpenCodeProperties("sh", "http://127.0.0.1:8080/v1", "qwen-4b",
                Map.of("qaa", "")), roles, "claude");

        assertThat(check.problems()).extracting(Problem::key).containsExactly("orchestrator.opencode.reads");
    }

    @Test
    void refusesReadsWithoutAnEndpointSoNoReadFallsToTheUsersOwnProvider() {
        var check = new OpenCodeCheck(new OpenCodeProperties("sh", "", "qwen-4b", Map.of("qa", "")), roles, "claude");

        assertThat(check.problems()).extracting(Problem::key).containsExactly("orchestrator.opencode.endpoint");
    }

    @Test
    void refusesARoleWhoseReadResolvesToNoModel() {
        var check = new OpenCodeCheck(new OpenCodeProperties("sh", "http://127.0.0.1:8080/v1", "", Map.of("master", "")),
                roles, "claude");

        assertThat(check.problems()).extracting(Problem::key).containsExactly("orchestrator.opencode.reads");
    }

    @Test
    void acceptsAnInstalledRoleAndMasterReadingOnTheirOwnAndTheDefaultModel() {
        var check = new OpenCodeCheck(new OpenCodeProperties("sh", "http://127.0.0.1:8080/v1", "qwen-4b",
                Map.of("qa", "qwen-9b", "master", "")), roles, "claude");

        assertThat(check.problems()).isEmpty();
    }

    @Test
    void answersTheModelARoleReadsOnAndNoneForARoleNotNamed() {
        var opencode = new OpenCodeProperties("sh", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("qa", "qwen-9b", "master", ""));

        assertThat(opencode.readModel("qa")).contains("qwen-9b");
        assertThat(opencode.readModel("master")).contains("qwen-4b");
        assertThat(opencode.readModel("review")).isEmpty();
    }
}
