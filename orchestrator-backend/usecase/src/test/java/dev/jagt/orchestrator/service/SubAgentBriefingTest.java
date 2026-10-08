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

        assertThat(briefing).contains("The deploy branch `dev` is jagt's `deploy` alone.");
    }

    @Test
    void namesTheDeployBranchOfEveryRepositoryOfTheTask() {
        NewRepo web = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);
        NewRepo api = new NewRepo("api", new ProjectConfig("/repo/api", "origin/main", "stage", List.of()),
                Path.of("/repo/api"), Path.of("/w/ABC-42-api"), Path.of("/repo/api/.git"), "origin/main",
                "git@host:g/api.git", false);

        String briefing = new SubAgentBriefing(new PromptTemplates())
                .of(NewTask.builder("ABC-42", "web").build(), web, List.of(web, api));

        assertThat(briefing).contains("The deploy branches `dev` (web), `stage` (api) are jagt's `deploy` alone.");
    }

    @Test
    void namesTheBaseBranchOfEveryRepositoryOfTheTaskReadOnly() {
        NewRepo web = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);
        NewRepo api = new NewRepo("api", new ProjectConfig("/repo/api", "origin/develop", "stage", List.of()),
                Path.of("/repo/api"), Path.of("/w/ABC-42-api"), Path.of("/repo/api/.git"), "origin/develop",
                "git@host:g/api.git", false);

        String briefing = new SubAgentBriefing(new PromptTemplates())
                .of(NewTask.builder("ABC-42", "web").build(), web, List.of(web, api));

        assertThat(briefing).contains("The base branches `origin/main` (web), `origin/develop` (api) are read-only.");
    }

    @Test
    void saysNothingOfADeployBranchWhenTheTaskHasNone() {
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", null, List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(new PromptTemplates())
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo));

        assertThat(briefing).doesNotContain("deploy branch");
    }
}
