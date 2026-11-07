package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.Remote;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * What belongs to the AGENT rather than to jagt stays behind {@link AgentRuntime}: this class never
 * learns what a given agent's config file is called.
 */
@Service
@RequiredArgsConstructor
public class TaskProvisioning {

  private final ConfigService configService;
  private final StateService stateService;
  private final NewTaskWorktrees worktrees;
  private final AgentSessions agentSessions;
  private final Clock clock;

  /** The git remote a project's branches are shared through. */
  public Remote remoteOf(String projectKey) {
    return configService.project(projectKey).gitRemote();
  }

  public String initializeTask(NewTask request) {
    if (request.projectKeys().isEmpty()) {
      return initializeUnplaced(request);
    }
    String taskId = request.taskId();
    ConfigService.ConfigFile config = configService.load();
    boolean plan = AgentSessions.planMode(request.mode());
    BranchStrategy strategy = BranchStrategy.of(request.branchStrategy());
    List<NewRepo> repos;
    String alias;
    // The cap and the duplicate checks in cut() read what putTask writes.
    synchronized (this) {
      repos = worktrees.cut(request, config, strategy);
      alias = nextAlias(taskId);
      try {
        // Only the OVERRIDE is persisted: a task that took the project default must keep following
        // it.
        register(
            request,
            config,
            repos.stream().map(NewRepo::registered).toList(),
            alias,
            request.baseBranch() == null || request.baseBranch().isBlank()
                ? null
                : repos.get(0).baseBranch(),
            worktrees.trust(repos));
      } catch (RuntimeException e) {
        worktrees.uncut(request, repos, strategy);
        throw e;
      }
    }
    NewRepo session = repos.get(0);
    return started(
        taskId,
        alias,
        session.worktreePath(),
        plan,
        "worktree created at " + session.worktreePath(),
        taskId
            + " is "
            + alias
            + " — agent running on "
            + taskId
            + (strategy == BranchStrategy.RESUME ? " (resumed)" : " from " + session.baseBranch())
            + alsoIn(repos)
            + ".");
  }

  private String initializeUnplaced(NewTask request) {
    String taskId = request.taskId();
    ConfigService.ConfigFile config = configService.load();
    boolean plan = AgentSessions.planMode(request.mode());
    Path folder;
    String alias;
    synchronized (this) {
      folder = worktrees.scratch(request, config);
      alias = nextAlias(taskId);
      try {
        register(
            request,
            config,
            List.of(TaskRepo.of(null, folder.toString())),
            alias,
            request.baseBranch() == null || request.baseBranch().isBlank()
                ? null
                : request.baseBranch(),
            null);
      } catch (RuntimeException e) {
        WorktreeFiles.deleteTree(folder);
        throw e;
      }
    }
    return started(
        taskId,
        alias,
        folder,
        plan,
        "scratch folder created at " + folder,
        taskId + " is " + alias + " — agent planning in " + folder + ", no project yet.");
  }

  private void register(
      NewTask request,
      ConfigService.ConfigFile config,
      List<TaskRepo> repos,
      String alias,
      @Nullable String baseBranch,
      @Nullable GitTrust trust) {
    TaskState born =
        TaskState.builder(repos, TaskStatus.NEW)
            .lastActiveTimestamp(clock.millis())
            .alias(alias)
            .title(request.title())
            .ticketUrl(
                request.ticketUrl() == null || request.ticketUrl().isBlank()
                    ? null
                    : request.ticketUrl())
            .baseBranch(baseBranch)
            .autoReview(config.autoReview().enabledOrDefault())
            .gitTrust(trust)
            .build();
    stateService.putTask(
        request.taskId(),
        TaskReducer.planAsked(
            autoplan(config) ? TaskReducer.autoplanned(born) : born,
            AgentSessions.planMode(request.mode())));
  }

  private String started(
      String taskId, String alias, Path folder, boolean plan, String created, String result) {
    try {
      agentSessions.startAgent(taskId, alias, folder, plan);
    } catch (RuntimeException e) {
      return "Task "
          + taskId
          + " registered and "
          + created
          + ", but the agent session failed to start: "
          + e.getMessage()
          + " Fix the cause and call open_task_tab(\""
          + taskId
          + "\") — do NOT call initialize_task again.";
    }
    return result + (plan ? " Plan mode: read plan.md, then approve-plan " + alias + "." : "");
  }

  private static boolean autoplan(ConfigService.ConfigFile config) {
    return config.agent().autoplanOrDefault().enabledOrDefault();
  }

  private static String alsoIn(List<NewRepo> repos) {
    return repos.size() < 2
        ? ""
        : ", also in "
            + repos.stream().skip(1).map(NewRepo::project).collect(Collectors.joining(", "));
  }

  private String nextAlias(String taskId) {
    String letter = taskId.substring(0, 1).toLowerCase(Locale.ROOT);
    var used =
        stateService.tasks().values().stream()
            .map(TaskState::alias)
            .filter(a -> a != null)
            .collect(Collectors.toSet());
    for (int i = 1; ; i++) {
      String candidate = letter + i;
      if (!used.contains(candidate)) {
        return candidate;
      }
    }
  }
}
