package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.Artifact;
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

    /** A step a ship instruction asks for, such as opening the request, is not left to a classifier's guess. */
    @Value("${orchestrator.agent-allowed-tools:}")
    private List<String> agentAllowedTools;

    /** Every worktree written again for the set that now includes {@code added}, which the session may edit. */
    public void attach(NewTask request, NewRepo added, List<NewRepo> repos) {
        repos.forEach(repo -> fill(request, repo, repos, repo.equals(added)
                ? agentRuntime.systemKnowledgeFile(repo.worktreePath()) : agentRuntime.briefedFile(repo.worktreePath())));
        agentRuntime.admit(repos.get(0).worktreePath(), added.worktreePath());
    }

    /** {@code repos} is every repository of the task, so the briefing can name the ones this agent may edit. */
    public void fill(NewTask request, NewRepo repo, List<NewRepo> repos) {
        // Asked while the worktree holds nothing but the checkout: jagt's own links look the same afterwards.
        fill(request, repo, repos, agentRuntime.systemKnowledgeFile(repo.worktreePath()));
    }

    private void fill(NewTask request, NewRepo repo, List<NewRepo> repos, Path systemKnowledge) {
        Path worktreePath = repo.worktreePath();
        WorktreeFiles.excludeOrchestratorPlumbing(repo.gitCommonDir(), agentRuntime);
        WorktreeHooks.install(worktreePath, request.taskId());
        // Which files exist and what is in them belongs to the runtime.
        agentRuntime.provisionWorktree(AgentWorktree.of(worktreePath, paths.root())
                .withOutputStyle(configService.load().agent().outputStyleOrNull())
                .withDisabledPlugins(agentDisabledPlugins)
                .withAllowedTools(agentAllowedTools)
                .withSandbox(WorktreeSandbox.of(configService.load().agent().sandboxOrDefault(), repos,
                        Path.of(System.getProperty("user.home")), Path.of(System.getProperty("java.io.tmpdir")))));
        GitDeploy.deployWorktreePaths(repos, request.taskId())
                .forEach(deployWorktree -> agentRuntime.admit(worktreePath, deployWorktree));
        WorktreeFiles.copyProjectFiles(repo.projectPath(), worktreePath, WorktreeFiles.PROJECT_FILES);
        WorktreeFiles.copyLocalFiles(repo.projectPath(), worktreePath,
                configService.load().worktree().copyGlobsOrDefault());
        WorktreeFiles.write(systemKnowledge, briefing.of(request, repo, repos,
                configService.load().agent().autoplanOrDefault(),
                configService.load().agent().visualizeOrDefault(),
                configService.load().agent().diagnoseOrDefault()));
        // The instructions live where the session reads them, and a relay writes to that same one worktree.
        if (repo.primary() && request.instructions() != null && !request.instructions().isBlank()) {
            WorktreeFiles.write(worktreePath.resolve(Artifact.CONTEXT.fileName()), request.instructions());
            WorktreeFiles.write(worktreePath.resolve(Artifact.REQUEST.fileName()), request.instructions());
        }
    }
}
