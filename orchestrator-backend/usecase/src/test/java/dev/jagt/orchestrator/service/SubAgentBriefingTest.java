package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubAgentBriefingTest {

    @Test
    void namesTheDeployBranchAsJagtsAloneSoTheAgentNeverMergesIntoIt() {
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(new PromptTemplates())
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo));

        assertThat(briefing).contains("The deploy branch (`dev`) is jagt's `deploy`");
    }
}
