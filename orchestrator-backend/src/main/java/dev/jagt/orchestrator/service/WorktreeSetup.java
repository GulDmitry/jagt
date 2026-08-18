package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.agent.AgentRuntime;
import dev.jagt.orchestrator.agent.AgentWorktree;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.model.NewRepo;
import dev.jagt.orchestrator.model.NewTask;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/** Everything a fresh worktree needs before an agent is started in it. */
@Service
@RequiredArgsConstructor
public class WorktreeSetup {

    private final AgentRuntime agentRuntime;
    private final OrchestratorPaths paths;
    private final ConfigService configService;
    private final SubAgentBriefing briefing;

    /** Heavy LSP plugins spawn a ~1-2GB server per worktree, so a RAM-constrained setup can opt out. */
    @Value("${orchestrator.agent-disabled-plugins:}")
    private List<String> agentDisabledPlugins;

    /**
     * @param repos every repository of the task, so the briefing can name the ones this agent may also edit
     */
    public void fill(NewTask request, NewRepo repo, List<NewRepo> repos) {
        Path worktreePath = repo.worktreePath();
        // Asked while the worktree still holds nothing but the checkout: after provisioning, jagt's own links
        // are indistinguishable from a file the repository ships.
        Path systemKnowledge = agentRuntime.systemKnowledgeFile(worktreePath);
        WorktreeFiles.excludeOrchestratorPlumbing(repo.gitCommonDir());
        // Which files exist and what is in them belongs to the runtime: nothing here may learn what a given
        // agent's MCP config is called.
        agentRuntime.provisionWorktree(new AgentWorktree(worktreePath, paths.root(),
                configService.load().agent().outputStyleOrNull(), agentDisabledPlugins));
        WorktreeFiles.copyIdeProjectFiles(repo.projectPath(), worktreePath);
        WorktreeFiles.copyLocalFiles(repo.projectPath(), worktreePath,
                configService.load().worktree().copyGlobsOrDefault());
        WorktreeFiles.write(systemKnowledge, briefing.of(request, repo, repos));
        // The instructions live where the session reads them, and a relay writes to that same one worktree.
        if (repo.primary() && request.instructions() != null && !request.instructions().isBlank()) {
            WorktreeFiles.write(worktreePath.resolve("task_context.md"), request.instructions());
        }
    }
}
