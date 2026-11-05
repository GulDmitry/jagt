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

    @ParameterizedTest
    @ValueSource(strings = {
            "# Plan\n",
            "## Repositories\n",
            "## Repositories\n- [unaffected: no change] api\n",
    })
    void refusesAPlanOfATaskWithNoRepositoryThatNamesNoChangedOne(String plan) {
        List<Violation> violations = PlanRepositories.violationsIn(plan, List.of());

        assertThat(violations).singleElement().asString().contains("## Repositories").contains("- [changed] <key>");
    }

    @Test
    void acceptsAnyChangedKeyForATaskWithNoRepository() {
        assertThat(PlanRepositories.violationsIn("## Repositories\n- [changed] billing\n", List.of())).isEmpty();
    }

    @Test
    void refusesATaskRepositoryTheSectionLeavesOut() {
        List<Violation> violations = PlanRepositories.violationsIn("## Repositories\n- [changed] api\n", List.of("api", "web"));

        assertThat(violations).singleElement().asString().contains("list `web`");
    }

    @Test
    void namesTheRepositoriesThePlanTagsUnaffected() {
        String plan = "## Repositories\n- [changed] api\n- [unaffected: only the API changes] web\n- [unaffected: nothing] docs\n";

        assertThat(PlanRepositories.unaffectedIn(plan, List.of("api", "web"))).containsExactly("web");
    }

    @Test
    void namesTheChangedKeysInThePlansOrder() {
        String plan = "## Repositories\n- [changed] web\n- [unaffected: nothing] docs\n- [changed] billing\n";

        assertThat(PlanRepositories.changedIn(plan)).containsExactly("web", "billing");
    }

    @Test
    void namesNoUnaffectedRepositoryOutsideTheSection() {
        String plan = "## Notes\n- [unaffected: x] web\n## Repositories\n- [changed] api\n- [changed] web\n";

        assertThat(PlanRepositories.unaffectedIn(plan, List.of("api", "web"))).isEmpty();
    }
}
