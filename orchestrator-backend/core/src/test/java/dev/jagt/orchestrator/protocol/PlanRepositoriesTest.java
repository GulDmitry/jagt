package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanRepositoriesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "- [changed] api\n- [changed] web",
            "- [changed] api\n- [unaffected: only the page text moves] web",
            "- [unaffected: the contract stays] api\n- [changed] web",
    })
    void acceptsAPlanNamingEveryRepositoryWithATag(String items) {
        List<Violation> violations = PlanRepositories.violationsIn("## Repositories\n" + items + "\n", List.of("api", "web"));

        assertThat(violations).isEmpty();
    }

    @Test
    void asksForNothingOfATaskWithOneRepository() {
        assertThat(PlanRepositories.violationsIn("# Plan\n", List.of("api"))).isEmpty();
    }

    @Test
    void refusesAPlanWithoutTheSectionNamingEveryRepository() {
        List<Violation> violations = PlanRepositories.violationsIn("## Acceptance criteria\n- [here] x\n", List.of("api", "web"));

        assertThat(violations).singleElement().asString().contains("## Repositories").contains("api, web");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "- web",
            "- [web]",
            "- [done] web",
            "- [unaffected] web",
            "- [unaffected: ] web",
    })
    void refusesARepositoryWithoutAValidTag(String item) {
        List<Violation> violations = PlanRepositories.violationsIn("## Repositories\n- [changed] api\n" + item + "\n",
                List.of("api", "web"));

        assertThat(violations).hasSize(2).anyMatch(violation -> violation.toString().contains(item));
    }

    @Test
    void refusesAKeyThatIsNoRepositoryOfTheTask() {
        List<Violation> violations = PlanRepositories.violationsIn(
                "## Repositories\n- [changed] api\n- [changed] web\n- [changed] docs\n", List.of("api", "web"));

        assertThat(violations).singleElement().asString().contains("`docs` is no repository of this task");
    }

    @Test
    void refusesATaskRepositoryTheSectionLeavesOut() {
        List<Violation> violations = PlanRepositories.violationsIn("## Repositories\n- [changed] api\n", List.of("api", "web"));

        assertThat(violations).singleElement().asString().contains("list `web`");
    }
}
