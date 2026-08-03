package dev.jagt.orchestrator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.model.ProjectConfig;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Reads config.json (user-maintained SSOT for projects). Re-read on every access
 * so edits are picked up without restarting the backend.
 *
 * <p>The file is organised into logical sections ({@code viewer}, {@code dashboard},
 * {@code codeReview}, {@code agent}, {@code worktree}, {@code projects}); each section is a small
 * value record with its own {@code defaults()} + {@code withX} withers and {@code *OrDefault}
 * accessors. A whole section may be omitted — {@link ConfigFile}'s accessors coalesce a missing
 * section to its all-default instance, so callers never null-check.
 */
@Service
public class ConfigService {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfigFile(Map<String, ProjectConfig> projects, ViewerConfig viewer, DashboardConfig dashboard,
                             CodeReviewConfig codeReview, AgentConfig agent, WorktreeConfig worktree) {

        /** Agent viewer window/tabs + the tmux session everything attaches to. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ViewerConfig(String tmuxSession, String viewMode, Boolean keepViewer) {

            public static ViewerConfig defaults() {
                return new ViewerConfig(null, null, null);
            }

            public ViewerConfig withTmuxSession(String session) {
                return new ViewerConfig(session, viewMode, keepViewer);
            }

            public ViewerConfig withViewMode(String mode) {
                return new ViewerConfig(tmuxSession, mode, keepViewer);
            }

            public ViewerConfig withKeepViewer(Boolean keep) {
                return new ViewerConfig(tmuxSession, viewMode, keep);
            }

            /** Default true: the agents window/tab stays open (reserved) after the last task is done. */
            public boolean keepViewerOrDefault() {
                return keepViewer == null || keepViewer;
            }

            /**
             * True for viewMode "shared" (default): every task is a tmux window in ONE session, one terminal
             * tab total. False for "tab-per-task": each task gets its own session, shown as its own tab.
             */
            public boolean sharedView() {
                return viewMode == null || "shared".equalsIgnoreCase(viewMode);
            }
        }

        /** Master TUI dashboard sizing. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record DashboardConfig(Integer refreshSeconds, Integer reservedRows) {

            public static DashboardConfig defaults() {
                return new DashboardConfig(null, null);
            }

            public DashboardConfig withRefreshSeconds(Integer seconds) {
                return new DashboardConfig(seconds, reservedRows);
            }

            public DashboardConfig withReservedRows(Integer rows) {
                return new DashboardConfig(refreshSeconds, rows);
            }

            /**
             * How often (seconds) the Master TUI repaints the dashboard. Default 10. {@code <= 0} disables the
             * periodic refresh (clamped to 0), so the screen redraws only on input or terminal resize.
             */
            public int refreshSecondsOrDefault() {
                if (refreshSeconds == null) {
                    return 10;
                }
                return refreshSeconds < 0 ? 0 : refreshSeconds;
            }

            /**
             * MINIMUM rows the Master shell keeps free ABOVE its pinned dashboard for the banner, command
             * output, and the prompt. The dashboard hugs its own content at the bottom regardless of terminal
             * size; this only CAPS how tall the pinned region may grow (beyond it, tasks overflow to a "… +N"
             * line). Default 17; negatives are clamped to 0.
             */
            public int reservedRowsOrDefault() {
                if (reservedRows == null) {
                    return 17;
                }
                return reservedRows < 0 ? 0 : reservedRows;
            }
        }

        /** Merge-request title + review-reply behaviour on {@code ship}. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record CodeReviewConfig(String mrTitlePattern, Boolean postReviewReplies,
                                       List<String> reviewReplyAuthors) {

            public static CodeReviewConfig defaults() {
                return new CodeReviewConfig(null, null, null);
            }

            public CodeReviewConfig withMrTitlePattern(String pattern) {
                return new CodeReviewConfig(pattern, postReviewReplies, reviewReplyAuthors);
            }

            public CodeReviewConfig withPostReviewReplies(Boolean post) {
                return new CodeReviewConfig(mrTitlePattern, post, reviewReplyAuthors);
            }

            public CodeReviewConfig withReviewReplyAuthors(List<String> authors) {
                return new CodeReviewConfig(mrTitlePattern, postReviewReplies, authors);
            }

            /** Placeholders {ticket} and {title}. Default: the ticket id, a space, then the Jira title. */
            public String mrTitlePatternOrDefault() {
                return mrTitlePattern == null || mrTitlePattern.isBlank() ? "{ticket} {title}" : mrTitlePattern;
            }

            /**
             * Default true: on `ship`, the agent posts its drafted review replies to the MR threads (current
             * behaviour). False: the replies stay in {@code review_replies.md} for the human — only code is
             * pushed. Either way the agent drafts a per-comment "comment -> intended reply" block each round.
             */
            public boolean postReviewRepliesOrDefault() {
                return postReviewReplies == null || postReviewReplies;
            }

            /**
             * Optional whitelist: when non-empty, drafted review replies are posted ONLY to threads whose
             * author matches one of these (case-insensitive substring, e.g. "coderabbit"). Empty = post to
             * every thread (the default). Only meaningful when {@link #postReviewRepliesOrDefault()} is true.
             */
            public List<String> reviewReplyAuthorsOrEmpty() {
                return reviewReplyAuthors == null ? List.of() : reviewReplyAuthors;
            }
        }

        /** Per-agent Claude Code settings written into each worktree. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record AgentConfig(String outputStyle) {

            public static AgentConfig defaults() {
                return new AgentConfig(null);
            }

            public AgentConfig withOutputStyle(String style) {
                return new AgentConfig(style);
            }

            /**
             * Optional Claude output style pinned into each agent worktree's settings. Default null:
             * nothing is written and agents use Claude's own resolved style. A fresh worktree is an
             * untrusted project where the human's global style may not apply, so this lets the human
             * force one (e.g. "sob-ai:Engineer") without jagt reading their global config.
             */
            public String outputStyleOrNull() {
                return outputStyle == null || outputStyle.isBlank() ? null : outputStyle.strip();
            }
        }

        /** Which gitignored local files get copied from the base repo into each new worktree. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record WorktreeConfig(List<String> copyGlobs) {

            public static WorktreeConfig defaults() {
                return new WorktreeConfig(null);
            }

            public WorktreeConfig withCopyGlobs(List<String> globs) {
                return new WorktreeConfig(globs);
            }

            /**
             * Glob patterns (relative to the repo root) of gitignored local files — secrets, keys, module
             * {@code .env}, SSL certs — to copy from the base repo into each new worktree so the app can
             * run there. Default {@code ["**}{@code /.env"]}; projects add their own (e.g. {@code
             * "**}{@code /*.pem"}, {@code "**}{@code /gcs-key-file.json"}). Not hardcoded — per project.
             */
            public List<String> copyGlobsOrDefault() {
                return copyGlobs == null || copyGlobs.isEmpty() ? List.of("**/.env") : copyGlobs;
            }
        }

        /** All-optional baseline: every section null, so each accessor coalesces to its section defaults. */
        public static ConfigFile defaults() {
            return new ConfigFile(Map.of(), null, null, null, null, null);
        }

        // Section accessors coalesce a missing (null) section to its all-default instance, so callers
        // reach through them (config.viewer().tmuxSession()) without null-checking. The raw field is
        // still what the withers copy, so an omitted section stays null in state until explicitly set.
        @Override
        public ViewerConfig viewer() {
            return viewer == null ? ViewerConfig.defaults() : viewer;
        }

        @Override
        public DashboardConfig dashboard() {
            return dashboard == null ? DashboardConfig.defaults() : dashboard;
        }

        @Override
        public CodeReviewConfig codeReview() {
            return codeReview == null ? CodeReviewConfig.defaults() : codeReview;
        }

        @Override
        public AgentConfig agent() {
            return agent == null ? AgentConfig.defaults() : agent;
        }

        @Override
        public WorktreeConfig worktree() {
            return worktree == null ? WorktreeConfig.defaults() : worktree;
        }

        public ConfigFile withProjects(Map<String, ProjectConfig> newProjects) {
            return new ConfigFile(newProjects, viewer, dashboard, codeReview, agent, worktree);
        }

        public ConfigFile withViewer(ViewerConfig newViewer) {
            return new ConfigFile(projects, newViewer, dashboard, codeReview, agent, worktree);
        }

        public ConfigFile withDashboard(DashboardConfig newDashboard) {
            return new ConfigFile(projects, viewer, newDashboard, codeReview, agent, worktree);
        }

        public ConfigFile withCodeReview(CodeReviewConfig newCodeReview) {
            return new ConfigFile(projects, viewer, dashboard, newCodeReview, agent, worktree);
        }

        public ConfigFile withAgent(AgentConfig newAgent) {
            return new ConfigFile(projects, viewer, dashboard, codeReview, newAgent, worktree);
        }

        public ConfigFile withWorktree(WorktreeConfig newWorktree) {
            return new ConfigFile(projects, viewer, dashboard, codeReview, agent, newWorktree);
        }
    }

    private final ObjectMapper mapper;
    private final OrchestratorPaths paths;

    public ConfigService(ObjectMapper mapper, OrchestratorPaths paths) {
        this.mapper = mapper;
        this.paths = paths;
    }

    public ConfigFile load() {
        if (!Files.exists(paths.configFile())) {
            throw new IllegalStateException("Missing " + paths.configFile()
                    + " — copy config.json.dist to config.json and fill in your projects.");
        }
        try {
            ConfigFile config = mapper.readValue(Files.readString(paths.configFile()), ConfigFile.class);
            return config.projects() == null ? config.withProjects(Map.of()) : config;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read config file " + paths.configFile(), e);
        }
    }

    public ProjectConfig project(String projectKey) {
        Map<String, ProjectConfig> projects = load().projects();
        ProjectConfig project = projects.get(projectKey);
        if (project == null) {
            throw new IllegalArgumentException(
                    "Unknown project '" + projectKey + "'. Known projects: " + projects.keySet());
        }
        return project;
    }
}
