package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

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

    /** {@code repos} is every repository of the task, so the briefing can name the ones this agent may edit. */
    public void fill(NewTask request, NewRepo repo, List<NewRepo> repos) {
        Path worktreePath = repo.worktreePath();
        // Asked while the worktree holds nothing but the checkout: jagt's own links look the same afterwards.
        Path systemKnowledge = agentRuntime.systemKnowledgeFile(worktreePath);
        WorktreeFiles.excludeOrchestratorPlumbing(repo.gitCommonDir(), agentRuntime);
        WorktreeHooks.install(worktreePath, request.taskId());
        // Which files exist and what is in them belongs to the runtime.
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
