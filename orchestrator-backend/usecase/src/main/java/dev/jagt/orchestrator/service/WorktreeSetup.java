package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.port.Sandbox;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.Artifact;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorktreeSetup {

    private final AgentRuntime agentRuntime;
    private final OrchestratorPaths paths;
    private final ConfigService configService;
    private final SubAgentBriefing briefing;

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
        provision(worktreePath, WorktreeSandbox.of(configService.load().agent().sandboxOrDefault(), repos,
                Path.of(System.getProperty("user.home")), Path.of(System.getProperty("java.io.tmpdir"))));
        GitDeploy.deployWorktreePaths(repos, request.taskId())
                .forEach(deployWorktree -> agentRuntime.admit(worktreePath, deployWorktree));
        WorktreeFiles.copyProjectFiles(repo.projectPath(), worktreePath, WorktreeFiles.PROJECT_FILES);
        WorktreeFiles.copyLocalFiles(repo.projectPath(), worktreePath,
                configService.load().worktree().copyGlobsOrDefault());
        WorktreeFiles.write(systemKnowledge, briefing.of(request, repo, repos,
                configService.load().agent().autoplanOrDefault(),
                configService.load().agent().visualizeOrDefault(),
                configService.load().agent().diagnoseOrDefault()));
        if (repo.primary()) {
            writeInstructions(worktreePath, request);
        }
    }

    /** The folder a task with no repository plans in, briefed and provisioned as a worktree is, without git. */
    public Path scratch(NewTask request) {
        Path folder = paths.scratch().resolve(TaskName.slug(request.taskId()));
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + folder, e);
        }
        Path systemKnowledge = agentRuntime.systemKnowledgeFile(folder);
        provision(folder, WorktreeSandbox.scratch(configService.load().agent().sandboxOrDefault(), folder,
                Path.of(System.getProperty("user.home")), Path.of(System.getProperty("java.io.tmpdir"))));
        WorktreeFiles.write(systemKnowledge, briefing.ofScratch(request,
                configService.load().agent().autoplanOrDefault(),
                configService.load().agent().visualizeOrDefault(),
                configService.load().agent().diagnoseOrDefault()));
        writeInstructions(folder, request);
        return folder;
    }

    /** Which files exist and what is in them belongs to the runtime. */
    private void provision(Path worktreePath, Sandbox sandbox) {
        agentRuntime.provisionWorktree(AgentWorktree.of(worktreePath, paths.root())
                .withAllowedTools(agentAllowedTools)
                .withSandbox(sandbox));
    }

    /** The instructions live where the session reads them, and a relay writes to that same one worktree. */
    private static void writeInstructions(Path worktreePath, NewTask request) {
        if (request.instructions() != null && !request.instructions().isBlank()) {
            WorktreeFiles.write(worktreePath.resolve(Artifact.CONTEXT.fileName()), request.instructions());
            WorktreeFiles.write(worktreePath.resolve(Artifact.REQUEST.fileName()), request.instructions());
        }
    }
}
