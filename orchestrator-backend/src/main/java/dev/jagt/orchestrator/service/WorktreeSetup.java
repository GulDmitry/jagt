package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.agent.AgentRuntime;
import dev.jagt.orchestrator.agent.AgentWorktree;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.model.ProjectConfig;
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

    public void fill(NewTask request, ProjectConfig project, Path projectPath, Path worktreePath,
                     Path gitCommonDir, String baseBranch, String remoteUrl) {
        WorktreeFiles.excludeOrchestratorPlumbing(gitCommonDir);
        // Which files exist and what is in them belongs to the runtime: nothing here may learn what a given
        // agent's MCP config is called.
        agentRuntime.provisionWorktree(new AgentWorktree(worktreePath, paths.root(),
                configService.load().agent().outputStyleOrNull(), agentDisabledPlugins));
        WorktreeFiles.copyIdeProjectFiles(projectPath, worktreePath);
        WorktreeFiles.copyLocalFiles(projectPath, worktreePath,
                configService.load().worktree().copyGlobsOrDefault());
        WorktreeFiles.write(worktreePath.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE),
                briefing.of(request, project, baseBranch, worktreePath, remoteUrl));
        if (request.instructions() != null && !request.instructions().isBlank()) {
            WorktreeFiles.write(worktreePath.resolve("task_context.md"), request.instructions());
        }
    }
}
