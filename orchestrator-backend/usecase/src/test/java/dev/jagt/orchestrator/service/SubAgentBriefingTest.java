package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubAgentBriefingTest {

    @Test
    void handsTheAuthorTheStandardWithoutTheReviewersOwnSection() {
        String brief = "# The standard\n\n| role | the question it asks |\n\n## For the reviewer only\n\nworked rounds\n";

        assertThat(SubAgentBriefing.shared(brief)).isEqualTo("# The standard\n\n| role | the question it asks |");
    }
}
